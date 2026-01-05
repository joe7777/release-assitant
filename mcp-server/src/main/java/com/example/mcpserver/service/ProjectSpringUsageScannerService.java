package com.example.mcpserver.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.ProjectSpringUsageInventory;
import com.example.mcpserver.dto.ProjectSpringUsageScanRequest;
import com.example.mcpserver.dto.ProjectSpringUsageScanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProjectSpringUsageScannerService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectSpringUsageScannerService.class);
    private static final int DEFAULT_MAX_FILES = 5000;
    private static final int DEFAULT_MAX_FILE_BYTES = 300_000;
    private static final int TOP_FILES_LIMIT = 200;
    private static final int TOP_IMPORTS_LIMIT = 100;
    private static final List<String> DEFAULT_EXCLUDE_GLOBS = List.of(
            "**/target/**",
            "**/build/**",
            "**/.git/**",
            "**/bin/**");
    private static final List<String> TEST_EXCLUDE_GLOBS = List.of(
            "**/src/test/**",
            "**/*Test.java",
            "**/*Tests.java");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([^;]+);");
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@[A-Za-z0-9_$.]+");
    private static final Pattern SPRING_PACKAGE_PATTERN = Pattern.compile("org\\.springframework\\.[A-Za-z0-9_.]+");
    private static final Pattern POM_PARENT_VERSION_PATTERN = Pattern.compile(
            "(?s)<parent>.*?<groupId>org\\.springframework\\.boot</groupId>.*?"
                    + "<artifactId>spring-boot-starter-parent</artifactId>.*?<version>([^<]+)</version>.*?</parent>");
    private static final Pattern POM_PROPERTY_VERSION_PATTERN = Pattern.compile(
            "<spring-boot\\.version>([^<]+)</spring-boot\\.version>");
    private static final Pattern POM_STARTER_PATTERN = Pattern.compile(
            "(?s)<dependency>.*?<groupId>org\\.springframework\\.boot</groupId>.*?"
                    + "<artifactId>(spring-boot-starter-[^<]+)</artifactId>.*?</dependency>");
    private static final Pattern GRADLE_STARTER_PATTERN = Pattern.compile(
            "org\\.springframework\\.boot:spring-boot-starter-[A-Za-z0-9-]+");

    private final WorkspaceService workspaceService;
    private final HashingService hashingService;
    private final IngestionLedger ingestionLedger;
    private final VectorStoreAddService vectorStoreAddService;
    private final ObjectMapper objectMapper;
    private final int chunkSize;
    private final int chunkOverlap;

    public ProjectSpringUsageScannerService(WorkspaceService workspaceService, HashingService hashingService,
            IngestionLedger ingestionLedger, VectorStoreAddService vectorStoreAddService, ObjectMapper objectMapper,
            @Value("${mcp.rag.chunk-size:800}") int chunkSize,
            @Value("${mcp.rag.chunk-overlap:80}") int chunkOverlap) {
        this.workspaceService = workspaceService;
        this.hashingService = hashingService;
        this.ingestionLedger = ingestionLedger;
        this.vectorStoreAddService = vectorStoreAddService;
        this.objectMapper = objectMapper;
        this.chunkSize = Math.max(1, chunkSize);
        this.chunkOverlap = Math.max(0, chunkOverlap);
    }

    public ProjectSpringUsageScanResponse scanSpringUsage(ProjectSpringUsageScanRequest request) throws IOException {
        if (request == null || request.workspaceId() == null || request.workspaceId().isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        long start = System.nanoTime();
        Path workspace = workspaceService.resolveWorkspace(request.workspaceId());
        if (!Files.exists(workspace)) {
            throw new IllegalArgumentException("workspaceId does not exist: " + request.workspaceId());
        }
        boolean includeTests = Boolean.TRUE.equals(request.includeTests());
        boolean force = Boolean.TRUE.equals(request.force());
        int maxFiles = request.maxFiles() != null ? request.maxFiles() : DEFAULT_MAX_FILES;
        int maxFileBytes = request.maxFileBytes() != null ? request.maxFileBytes() : DEFAULT_MAX_FILE_BYTES;

        GitInfo gitInfo = resolveGitInfo(workspace);
        StarterDetection starterDetection = detectStartersAndVersion(workspace, maxFileBytes);
        ScanAggregation aggregation = scanJavaFiles(workspace, includeTests, maxFiles, maxFileBytes);

        List<ProjectSpringUsageInventory.ImportCount> topImports = aggregation.importCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_IMPORTS_LIMIT)
                .map(entry -> new ProjectSpringUsageInventory.ImportCount(entry.getKey(), entry.getValue()))
                .toList();

        List<ProjectSpringUsageInventory.ProjectSpringUsageFile> topFiles = aggregation.files().stream()
                .sorted(Comparator.comparingInt(FileUsage::score).reversed())
                .limit(TOP_FILES_LIMIT)
                .map(file -> new ProjectSpringUsageInventory.ProjectSpringUsageFile(
                        file.path(),
                        file.springImports().stream().sorted().toList(),
                        file.annotations().stream().sorted().toList()))
                .toList();

        List<String> springModules = guessSpringModules(aggregation.springImports(), starterDetection.starters());
        String commitOrHash = !"unknown".equals(gitInfo.commit())
                ? gitInfo.commit()
                : hashingService.sha256(request.workspaceId());
        String documentKey = "PROJECT_FACT/" + request.workspaceId() + "/" + commitOrHash + "/spring-usage-inventory";
        String version = starterDetection.springBootVersion() != null
                ? starterDetection.springBootVersion()
                : "unknown";

        ProjectSpringUsageInventory inventory = new ProjectSpringUsageInventory(
                gitInfo.repoUrl(),
                gitInfo.commit(),
                version,
                starterDetection.starters(),
                topImports,
                aggregation.springImports().stream().sorted().toList(),
                aggregation.annotations().stream().sorted().toList(),
                aggregation.packages().stream().sorted().toList(),
                topFiles);

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(inventory);
        String documentHash = hashingService.sha256(documentKey + json);
        boolean ingested = true;
        if (!force && ingestionLedger.alreadyIngested(documentHash)) {
            ingested = false;
        } else {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceType", "PROJECT_FACT");
            metadata.put("docKind", "PROJECT_FACT");
            metadata.put("library", "project");
            metadata.put("version", version);
            metadata.put("workspaceId", request.workspaceId());
            metadata.put("repoUrl", gitInfo.repoUrl());
            metadata.put("commit", gitInfo.commit());
            metadata.put("documentKey", documentKey);
            List<Document> documents = new ArrayList<>();
            int index = 0;
            for (String chunk : chunk(json, chunkSize, chunkOverlap)) {
                Map<String, Object> chunkMetadata = new HashMap<>(metadata);
                chunkMetadata.put("chunkIndex", index++);
                chunkMetadata.put("chunkHash", hashingService.sha256(documentHash + chunk));
                documents.add(new Document(chunk, chunkMetadata));
            }
            if (documents.isEmpty()) {
                documents.add(new Document(json, metadata));
            }
            vectorStoreAddService.add(documents);
            ingestionLedger.record(documentHash);
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        return new ProjectSpringUsageScanResponse(
                request.workspaceId(),
                aggregation.filesScanned(),
                aggregation.javaFilesParsed(),
                aggregation.importsCount(),
                aggregation.springImportsCount(),
                aggregation.annotationsCount(),
                springModules,
                starterDetection.starters(),
                documentKey,
                ingested,
                durationMs);
    }

    private ScanAggregation scanJavaFiles(Path workspace, boolean includeTests, int maxFiles, int maxFileBytes)
            throws IOException {
        Set<String> springImports = new HashSet<>();
        Set<String> annotations = new HashSet<>();
        Set<String> packages = new HashSet<>();
        Map<String, Integer> importCounts = new HashMap<>();
        List<FileUsage> files = new ArrayList<>();
        List<PathMatcher> excludeMatchers = resolveExcludeMatchers(includeTests);
        int filesScanned = 0;
        int javaFilesParsed = 0;
        int importsCount = 0;
        int springImportsCount = 0;
        int annotationsCount = 0;

        try (Stream<Path> stream = Files.walk(workspace)) {
            var iterator = stream.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                if (filesScanned >= maxFiles) {
                    logger.info("Spring usage scan stopped at maxFiles={}", maxFiles);
                    break;
                }
                Path file = iterator.next();
                String relativePath = workspace.relativize(file).toString().replace("\\", "/");
                if (!relativePath.endsWith(".java")) {
                    continue;
                }
                if (matches(excludeMatchers, relativePath)) {
                    continue;
                }
                filesScanned++;
                long size = Files.size(file);
                if (size > maxFileBytes) {
                    continue;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                javaFilesParsed++;

                List<String> fileSpringImports = new ArrayList<>();
                Set<String> fileAnnotations = new LinkedHashSet<>();
                int fileScore = 0;
                for (String line : content.split("\\R")) {
                    Matcher importMatcher = IMPORT_PATTERN.matcher(line);
                    if (importMatcher.find()) {
                        String importName = importMatcher.group(1).trim();
                        importsCount++;
                        importCounts.merge(importName, 1, Integer::sum);
                        if (importName.contains("org.springframework.")) {
                            springImportsCount++;
                            springImports.add(importName);
                            fileSpringImports.add(importName);
                            fileScore++;
                        }
        }
    }

    private List<String> chunk(String content, int chunkSize, int overlap) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, chunkSize - overlap);
        for (int start = 0; start < content.length(); start += step) {
            int end = Math.min(content.length(), start + chunkSize);
            chunks.add(content.substring(start, end));
            if (end >= content.length()) {
                break;
            }
        }
        return chunks;
    }

                Matcher annotationMatcher = ANNOTATION_PATTERN.matcher(content);
                while (annotationMatcher.find()) {
                    String annotation = annotationMatcher.group();
                    annotationsCount++;
                    annotations.add(annotation);
                    fileAnnotations.add(annotation);
                    fileScore++;
                }

                Matcher packageMatcher = SPRING_PACKAGE_PATTERN.matcher(content);
                while (packageMatcher.find()) {
                    packages.add(packageMatcher.group());
                }

                if (!fileSpringImports.isEmpty() || !fileAnnotations.isEmpty()) {
                    files.add(new FileUsage(relativePath, fileSpringImports, new ArrayList<>(fileAnnotations), fileScore));
                }
            }
        }

        return new ScanAggregation(filesScanned, javaFilesParsed, importsCount, springImportsCount, annotationsCount,
                importCounts, springImports, annotations, packages, files);
    }

    private StarterDetection detectStartersAndVersion(Path workspace, int maxFileBytes) throws IOException {
        Set<String> starters = new HashSet<>();
        String springBootVersion = null;
        List<PathMatcher> excludeMatchers = resolveExcludeMatchers(true);

        try (Stream<Path> stream = Files.walk(workspace)) {
            var iterator = stream.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                String relativePath = workspace.relativize(file).toString().replace("\\", "/");
                if (matches(excludeMatchers, relativePath)) {
                    continue;
                }
                String fileName = file.getFileName().toString();
                if (!fileName.equals("pom.xml") && !fileName.equals("build.gradle") && !fileName.equals("build.gradle.kts")) {
                    continue;
                }
                if (Files.size(file) > maxFileBytes) {
                    continue;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (fileName.equals("pom.xml")) {
                    starters.addAll(extractPomStarters(content));
                    if (springBootVersion == null) {
                        springBootVersion = extractPomBootVersion(content).orElse(null);
                    }
                } else {
                    starters.addAll(extractGradleStarters(content));
                }
            }
        }

        List<String> sorted = starters.stream().sorted().toList();
        return new StarterDetection(sorted, springBootVersion);
    }

    private Set<String> extractPomStarters(String content) {
        Set<String> starters = new HashSet<>();
        Matcher matcher = POM_STARTER_PATTERN.matcher(content);
        while (matcher.find()) {
            String starter = matcher.group(1).trim();
            if (!starter.isBlank() && !starter.equals("spring-boot-starter-parent")) {
                starters.add(starter);
            }
        }
        return starters;
    }

    private Optional<String> extractPomBootVersion(String content) {
        Matcher propertyMatcher = POM_PROPERTY_VERSION_PATTERN.matcher(content);
        if (propertyMatcher.find()) {
            return Optional.of(propertyMatcher.group(1).trim());
        }
        Matcher parentMatcher = POM_PARENT_VERSION_PATTERN.matcher(content);
        if (parentMatcher.find()) {
            return Optional.of(parentMatcher.group(1).trim());
        }
        return Optional.empty();
    }

    private Set<String> extractGradleStarters(String content) {
        Set<String> starters = new HashSet<>();
        Matcher matcher = GRADLE_STARTER_PATTERN.matcher(content);
        while (matcher.find()) {
            String value = matcher.group();
            int idx = value.lastIndexOf(':');
            if (idx > 0 && idx + 1 < value.length()) {
                starters.add(value.substring(idx + 1));
            }
        }
        return starters;
    }

    private List<PathMatcher> resolveExcludeMatchers(boolean includeTests) {
        List<String> globs = new ArrayList<>(DEFAULT_EXCLUDE_GLOBS);
        if (!includeTests) {
            globs.addAll(TEST_EXCLUDE_GLOBS);
        }
        return globs.stream()
                .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
                .toList();
    }

    private boolean matches(List<PathMatcher> matchers, String relativePath) {
        if (matchers.isEmpty()) {
            return false;
        }
        Path path = Path.of(relativePath);
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private GitInfo resolveGitInfo(Path workspace) {
        String commit = "unknown";
        String repoUrl = "unknown";
        if (Files.exists(workspace.resolve(".git"))) {
            try (Git git = Git.open(workspace.toFile())) {
                if (git.getRepository().resolve("HEAD") != null) {
                    commit = git.getRepository().resolve("HEAD").name();
                }
                StoredConfig config = git.getRepository().getConfig();
                String url = config.getString("remote", "origin", "url");
                if (url != null && !url.isBlank()) {
                    repoUrl = url.trim();
                }
            } catch (Exception ex) {
                logger.debug("Unable to resolve git metadata for {}", workspace, ex);
            }
        }
        return new GitInfo(repoUrl, commit);
    }

    private List<String> guessSpringModules(Set<String> springImports, List<String> starters) {
        Set<String> modules = new LinkedHashSet<>();
        Set<String> lowerStarters = starters.stream()
                .map(starter -> starter.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (hasStarter(lowerStarters, "spring-boot-starter-webflux") || containsImport(springImports, "org.springframework.web.reactive")) {
            modules.add("spring-webflux");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-web") || containsImport(springImports, "org.springframework.web")) {
            modules.add("spring-webmvc");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-data-jpa") || containsImport(springImports, "org.springframework.data.jpa")) {
            modules.add("spring-data-jpa");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-data-mongodb") || containsImport(springImports, "org.springframework.data.mongodb")) {
            modules.add("spring-data-mongodb");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-security") || containsImport(springImports, "org.springframework.security")) {
            modules.add("spring-security");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-validation") || containsImport(springImports, "org.springframework.validation")) {
            modules.add("spring-validation");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-actuator") || containsImport(springImports, "org.springframework.boot.actuate")) {
            modules.add("spring-boot-actuator");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-amqp") || containsImport(springImports, "org.springframework.amqp")) {
            modules.add("spring-amqp");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-jdbc") || containsImport(springImports, "org.springframework.jdbc")) {
            modules.add("spring-jdbc");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-data-redis") || containsImport(springImports, "org.springframework.data.redis")) {
            modules.add("spring-data-redis");
        }
        if (hasStarter(lowerStarters, "spring-boot-starter-cache") || containsImport(springImports, "org.springframework.cache")) {
            modules.add("spring-cache");
        }
        return new ArrayList<>(modules);
    }

    private boolean hasStarter(Set<String> starters, String starter) {
        return starters.contains(starter);
    }

    private boolean containsImport(Set<String> imports, String prefix) {
        return imports.stream().anyMatch(value -> value.startsWith(prefix));
    }

    private record ScanAggregation(int filesScanned, int javaFilesParsed, int importsCount, int springImportsCount,
                                   int annotationsCount, Map<String, Integer> importCounts, Set<String> springImports,
                                   Set<String> annotations, Set<String> packages, List<FileUsage> files) {
    }

    private record FileUsage(String path, List<String> springImports, List<String> annotations, int score) {
    }

    private record StarterDetection(List<String> starters, String springBootVersion) {
    }

    private record GitInfo(String repoUrl, String commit) {
    }
}
