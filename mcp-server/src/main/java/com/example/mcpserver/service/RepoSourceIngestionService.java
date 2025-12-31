package com.example.mcpserver.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.SpringSourceIngestionRequest;
import com.example.mcpserver.dto.SpringSourceIngestionResponse;

@Service
public class RepoSourceIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(RepoSourceIngestionService.class);
    private static final List<String> DEFAULT_TEST_EXCLUDE_GLOBS = List.of(
            "**/src/test/**",
            "**/src/it/**",
            "**/src/integrationTest/**",
            "**/*Test.java",
            "**/*Tests.java");
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".class", ".jar", ".war", ".zip", ".tar", ".gz", ".7z", ".png", ".jpg", ".jpeg", ".gif",
            ".bmp", ".ico", ".pdf", ".mp3", ".mp4", ".avi", ".mov", ".woff", ".woff2", ".ttf");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w\\.]+)\\s*;?");

    private final VectorStoreAddService vectorStoreAddService;
    private final HashingService hashingService;
    private final IngestionLedger ingestionLedger;
    private final Path cacheRoot;
    private final int defaultMaxFiles;
    private final int defaultMaxFileBytes;
    private final int defaultMaxLinesPerFile;
    private final int chunkSize;
    private final int chunkOverlap;
    private final boolean normalizeWhitespace;
    private final boolean defaultIncludeTests;
    private final Set<String> allowedSpringRepoUrls;
    private final Set<String> allowedProjectRepoUrls;

    public RepoSourceIngestionService(VectorStoreAddService vectorStoreAddService, HashingService hashingService,
            IngestionLedger ingestionLedger,
            @Value("${mcp.spring-source.cache-root:spring-sources}") String cacheRoot,
            @Value("${mcp.spring-source.default-max-files:10000}") int defaultMaxFiles,
            @Value("${mcp.spring-source.default-max-file-bytes:600000}") int defaultMaxFileBytes,
            @Value("${mcp.spring-source.default-max-lines-per-file:20000}") int defaultMaxLinesPerFile,
            @Value("${mcp.rag.chunk-size:800}") int chunkSize,
            @Value("${mcp.rag.chunk-overlap:80}") int chunkOverlap,
            @Value("${mcp.rag.text-normalization:true}") boolean normalizeWhitespace,
            @Value("${mcp.spring-source.include-tests:false}") boolean defaultIncludeTests,
            @Value("${mcp.repo-source.allowlist:"
                    + "https://github.com/spring-projects/spring-framework,"
                    + "https://github.com/spring-projects/spring-boot}") List<String> allowedRepoUrls,
            @Value("${mcp.project-source.allowlist:}") List<String> projectRepoUrls)
            throws IOException {
        this.vectorStoreAddService = vectorStoreAddService;
        this.hashingService = hashingService;
        this.ingestionLedger = ingestionLedger;
        this.cacheRoot = Path.of(cacheRoot).toAbsolutePath();
        Files.createDirectories(this.cacheRoot);
        this.defaultMaxFiles = defaultMaxFiles;
        this.defaultMaxFileBytes = defaultMaxFileBytes;
        this.defaultMaxLinesPerFile = defaultMaxLinesPerFile;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.normalizeWhitespace = normalizeWhitespace;
        this.defaultIncludeTests = defaultIncludeTests;
        this.allowedSpringRepoUrls = sanitizeAllowlist(allowedRepoUrls);
        this.allowedProjectRepoUrls = sanitizeAllowlist(projectRepoUrls);
    }

    public SpringSourceIngestionResponse ingest(SpringSourceIngestionRequest request, RepoSourceConfig config)
            throws IOException, GitAPIException {
        if (request == null || request.version() == null || request.version().isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        long startTime = System.nanoTime();
        boolean includeTestsResolved = request.includeTests() != null ? request.includeTests() : defaultIncludeTests;
        boolean includeNonJavaResolved = Boolean.TRUE.equals(request.includeNonJava());
        boolean includeKotlinResolved = Boolean.TRUE.equals(request.includeKotlin());
        boolean forceIngest = Boolean.TRUE.equals(request.force());
        int maxFilesResolved = request.maxFiles() != null ? request.maxFiles() : defaultMaxFiles;
        int maxFileBytesResolved = request.maxFileBytes() != null ? request.maxFileBytes() : defaultMaxFileBytes;
        int maxLinesResolved = request.maxLinesPerFile() != null ? request.maxLinesPerFile() : defaultMaxLinesPerFile;
        int chunkSizeResolved = request.chunkSize() != null ? request.chunkSize() : chunkSize;
        int chunkOverlapResolved = request.chunkOverlap() != null ? request.chunkOverlap() : chunkOverlap;
        String ref = resolveRef(request.version(), config.refPrefix());

        List<String> includeGlobs = resolveIncludeGlobs(request, includeKotlinResolved, config.defaultIncludeGlobs());
        List<String> excludeGlobs = resolveExcludeGlobs(request, includeTestsResolved, config.defaultExcludeGlobs());
        List<PathMatcher> includeMatchers = toMatchers(includeGlobs);
        List<PathMatcher> excludeMatchers = toMatchers(excludeGlobs);
        List<PathMatcher> testMatchers = includeTestsResolved ? List.of() : toMatchers(DEFAULT_TEST_EXCLUDE_GLOBS);

        List<String> modulePatterns = normalizeModulePatterns(request.modules(), config.defaultModules());
        List<PathMatcher> moduleMatchers = toMatchers(modulePatterns);
        Path repoPath = ensureRepo(config);
        String commit;
        try (Git git = Git.open(repoPath.toFile())) {
            fetch(git);
            checkoutRef(git, ref);
            commit = git.getRepository().resolve("HEAD").name();
        }

        IngestionStats stats = ingestFromRepo(repoPath, request.version(), commit, modulePatterns, moduleMatchers,
                includeMatchers, excludeMatchers, testMatchers, includeNonJavaResolved, includeKotlinResolved,
                maxFilesResolved, maxFileBytesResolved, maxLinesResolved, chunkSizeResolved, chunkOverlapResolved,
                forceIngest, config);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000L;
        return new SpringSourceIngestionResponse(request.version(),
                sanitizeModulesList(request.modules(), config.defaultModules()),
                stats.filesScanned(), stats.filesIngested(), stats.filesSkipped(), stats.skipReasons(),
                durationMs);
    }

    private IngestionStats ingestFromRepo(Path repoPath, String version, String commit,
            List<String> modulePatterns, List<PathMatcher> moduleMatchers, List<PathMatcher> includeMatchers,
            List<PathMatcher> excludeMatchers, List<PathMatcher> testMatchers, boolean includeNonJava,
            boolean includeKotlin, int maxFiles, int maxFileBytes, int maxLinesPerFile, int chunkSize,
            int chunkOverlap, boolean forceIngest, RepoSourceConfig config)
            throws IOException {
        Map<String, Integer> skipReasons = new HashMap<>();
        int filesScanned = 0;
        int filesIngested = 0;
        int filesSkipped = 0;

        try (Stream<Path> stream = Files.walk(repoPath)) {
            var iterator = stream.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                if (filesIngested >= maxFiles) {
                    logger.info("Source ingestion stopped at maxFiles={}", maxFiles);
                    increment(skipReasons, "MAX_FILES");
                    break;
                }
                Path file = iterator.next();
                filesScanned++;

                String relativePath = repoPath.relativize(file).toString().replace("\\", "/");
                String module = resolveModule(relativePath, modulePatterns);
                if (!moduleMatchers.isEmpty() && !matchesAny(relativePath, moduleMatchers)) {
                    filesSkipped++;
                    increment(skipReasons, "MODULE_MISMATCH");
                    continue;
                }
                if (!includeNonJava && !isAllowedSource(relativePath, includeKotlin)) {
                    filesSkipped++;
                    increment(skipReasons, "NON_JAVA");
                    continue;
                }
                if (!matchesAny(relativePath, includeMatchers)) {
                    filesSkipped++;
                    increment(skipReasons, "NOT_INCLUDED");
                    continue;
                }
                if (!testMatchers.isEmpty() && matchesAny(relativePath, testMatchers)) {
                    filesSkipped++;
                    increment(skipReasons, "TEST");
                    continue;
                }
                if (matchesAny(relativePath, excludeMatchers)) {
                    filesSkipped++;
                    increment(skipReasons, "EXCLUDED");
                    continue;
                }
                long fileSize = Files.size(file);
                if (fileSize > maxFileBytes) {
                    filesSkipped++;
                    increment(skipReasons, "TOO_LARGE");
                    continue;
                }
                if (isBinary(file)) {
                    filesSkipped++;
                    increment(skipReasons, "BINARY");
                    continue;
                }

                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.isBlank()) {
                    filesSkipped++;
                    increment(skipReasons, "EMPTY");
                    continue;
                }
                long lineCount = content.lines().count();
                if (lineCount > maxLinesPerFile) {
                    filesSkipped++;
                    increment(skipReasons, "TOO_MANY_LINES");
                    continue;
                }
                if (normalizeWhitespace) {
                    content = content.replaceAll("\\s+", " ").trim();
                }

                String documentKey = buildDocumentKey(config, version, module, relativePath);
                String documentHash = hashingService.sha256(config.sourceType() + "|" + config.library() + "|"
                        + version + "|" + relativePath + "|" + content);
                if (!forceIngest && ingestionLedger.alreadyIngested(documentHash)) {
                    filesSkipped++;
                    increment(skipReasons, "DUPLICATE");
                    continue;
                }

                List<String> chunks = chunk(content, chunkSize, chunkOverlap);
                if (chunks.isEmpty()) {
                    filesSkipped++;
                    increment(skipReasons, "EMPTY");
                    continue;
                }

                List<Document> documents = new ArrayList<>();
                String packageName = extractPackageName(content);
                String className = extractClassName(relativePath);
                if (className.isBlank()) {
                    className = "unknown";
                }
                int chunkIndex = 0;
                boolean allChunksDuplicate = true;
                for (String chunk : chunks) {
                    String chunkTextHash = hashingService.sha256(documentHash + chunk);
                    if (!forceIngest && ingestionLedger.alreadyIngestedChunk(chunkTextHash)) {
                        chunkIndex++;
                        continue;
                    }
                    allChunksDuplicate = false;
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("sourceType", config.sourceType());
                    metadata.put("library", config.library());
                    metadata.put("version", version);
                    metadata.put("repoUrl", config.repoUrl());
                    metadata.put("commit", commit);
                    metadata.put("module", module);
                    metadata.put("filePath", relativePath);
                    metadata.put("className", className);
                    metadata.put("packageName", packageName);
                    metadata.put("documentKey", documentKey);
                    metadata.put("documentHash", documentHash);
                    metadata.put("chunkTextHash", chunkTextHash);
                    metadata.put("chunkIndex", chunkIndex);
                    metadata.put("doc_content", content);
                    documents.add(new Document(chunk, metadata));
                    chunkIndex++;
                }

                if (documents.isEmpty()) {
                    filesSkipped++;
                    increment(skipReasons, allChunksDuplicate ? "DUPLICATE" : "EMPTY");
                    continue;
                }
                vectorStoreAddService.add(documents);
                ingestionLedger.record(documentHash);
                for (Document document : documents) {
                    Object chunkHash = document.getMetadata().get("chunkTextHash");
                    if (chunkHash != null) {
                        ingestionLedger.recordChunk(chunkHash.toString());
                    }
                }
                filesIngested++;
            }
        }

        return new IngestionStats(filesScanned, filesIngested, filesSkipped, skipReasons);
    }

    private List<String> resolveIncludeGlobs(SpringSourceIngestionRequest request, boolean includeKotlin,
            List<String> defaultIncludeGlobs) {
        List<String> includeGlobs = request.includeGlobs();
        List<String> resolved = includeGlobs == null || includeGlobs.isEmpty()
                ? new ArrayList<>(defaultIncludeGlobs)
                : new ArrayList<>(includeGlobs);
        if (includeKotlin && resolved.stream().noneMatch(glob -> glob.endsWith(".kt"))) {
            resolved.add("**/*.kt");
        }
        return resolved;
    }

    private List<String> resolveExcludeGlobs(SpringSourceIngestionRequest request, boolean includeTests,
            List<String> defaultExcludeGlobs) {
        List<String> excludeGlobs = request.excludeGlobs();
        List<String> resolved = excludeGlobs == null || excludeGlobs.isEmpty()
                ? new ArrayList<>(defaultExcludeGlobs)
                : new ArrayList<>(excludeGlobs);
        if (includeTests && excludeGlobs == null) {
            resolved.removeAll(DEFAULT_TEST_EXCLUDE_GLOBS);
        }
        if (!includeTests) {
            for (String pattern : DEFAULT_TEST_EXCLUDE_GLOBS) {
                if (!resolved.contains(pattern)) {
                    resolved.add(pattern);
                }
            }
        }
        return resolved;
    }

    private List<PathMatcher> toMatchers(List<String> globs) {
        if (globs == null || globs.isEmpty()) {
            return List.of();
        }
        return globs.stream()
                .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
                .collect(Collectors.toList());
    }

    private boolean matchesAny(String relativePath, List<PathMatcher> matchers) {
        if (matchers == null || matchers.isEmpty()) {
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

    private List<String> normalizeModulePatterns(List<String> modules, List<String> defaultModules) {
        List<String> source = modules == null || modules.isEmpty() ? defaultModules : modules;
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String module : source) {
            if (module == null || module.isBlank()) {
                continue;
            }
            String trimmed = module.trim();
            if (!containsGlob(trimmed) && !trimmed.endsWith("/**")) {
                trimmed = trimmed + "/**";
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private List<String> sanitizeModulesList(List<String> modules, List<String> defaultModules) {
        if (modules == null || modules.isEmpty()) {
            return defaultModules == null ? List.of() : List.copyOf(defaultModules);
        }
        return modules.stream()
                .filter(module -> module != null && !module.isBlank())
                .collect(Collectors.toList());
    }

    private Path ensureRepo(RepoSourceConfig config) throws GitAPIException {
        String repoUrl = config.repoUrl();
        if (isRepoRestricted(config)) {
            throw new IllegalArgumentException("Repository not allowed: " + repoUrl);
        }
        Path repoPath = cacheRoot.resolve(config.repoSlug());
        if (Files.exists(repoPath.resolve(".git"))) {
            return repoPath;
        }
        logger.info("Cloning repository {} into {}", repoUrl, repoPath);
        Git.cloneRepository().setURI(repoUrl).setDirectory(repoPath.toFile()).call().close();
        return repoPath;
    }

    private Set<String> sanitizeAllowlist(List<String> allowlist) {
        if (allowlist == null || allowlist.isEmpty()) {
            return Set.of();
        }
        Set<String> allowed = new HashSet<>();
        allowlist.stream()
                .filter(url -> url != null && !url.isBlank())
                .forEach(allowed::add);
        return Set.copyOf(allowed);
    }

    private boolean isRepoRestricted(RepoSourceConfig config) {
        Set<String> allowlist = isProjectSource(config) ? allowedProjectRepoUrls : allowedSpringRepoUrls;
        return !allowlist.isEmpty() && !allowlist.contains(config.repoUrl());
    }

    private boolean isProjectSource(RepoSourceConfig config) {
        if (config == null) {
            return false;
        }
        String documentKeyPrefix = config.documentKeyPrefix();
        if (documentKeyPrefix != null && !documentKeyPrefix.isBlank()) {
            return documentKeyPrefix.toUpperCase(Locale.ROOT).startsWith("PROJECT");
        }
        String sourceType = config.sourceType();
        return sourceType != null && sourceType.toUpperCase(Locale.ROOT).startsWith("PROJECT");
    }

    private void fetch(Git git) {
        try {
            git.fetch().setRemote("origin").call();
        }
        catch (GitAPIException e) {
            logger.warn("Unable to fetch latest refs", e);
        }
    }

    private void checkoutRef(Git git, String ref) throws GitAPIException {
        List<String> candidates = List.of(ref, "refs/tags/" + ref, "origin/" + ref);
        GitAPIException last = null;
        for (String candidate : candidates) {
            try {
                git.checkout().setName(candidate).setForced(true).call();
                return;
            }
            catch (GitAPIException e) {
                last = e;
            }
        }
        throw last != null ? last : new GitAPIException("Unable to checkout " + ref) {
        };
    }

    private String resolveRef(String version, String refPrefix) {
        if (refPrefix == null || refPrefix.isBlank()) {
            return version;
        }
        String lowerPrefix = refPrefix.toLowerCase(Locale.ROOT);
        if (version.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
            return version;
        }
        return refPrefix + version;
    }

    private boolean isAllowedSource(String relativePath, boolean includeKotlin) {
        String name = relativePath.toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) {
            return true;
        }
        return includeKotlin && name.endsWith(".kt");
    }

    private boolean isBinary(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : BINARY_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            for (byte b : bytes) {
                if (b == 0) {
                    return true;
                }
            }
            return false;
        }
        catch (IOException e) {
            logger.warn("Unable to inspect file {}", file, e);
            return true;
        }
    }

    private String extractModule(String relativePath) {
        int idx = relativePath.indexOf('/');
        return idx > 0 ? relativePath.substring(0, idx) : relativePath;
    }

    private boolean containsGlob(String value) {
        return value.contains("*") || value.contains("?") || value.contains("[");
    }

    private String resolveModule(String relativePath, List<String> modulePatterns) {
        if (modulePatterns == null || modulePatterns.isEmpty()) {
            return extractModule(relativePath);
        }
        for (String pattern : modulePatterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            if (matcher.matches(Path.of(relativePath))) {
                String normalized = pattern.endsWith("/**")
                        ? pattern.substring(0, pattern.length() - 3)
                        : pattern;
                return normalized;
            }
        }
        return extractModule(relativePath);
    }

    private String buildDocumentKey(RepoSourceConfig config, String version, String module, String relativePath) {
        return config.documentKeyPrefix() + "/" + config.library() + "/" + version + "/" + module + "/" + relativePath;
    }

    private String extractClassName(String relativePath) {
        String name = Path.of(relativePath).getFileName().toString();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private List<String> chunk(String content, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int step = Math.max(1, chunkSize - overlap);
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            result.add(content.substring(start, end));
            if (end >= content.length()) {
                break;
            }
            start = Math.min(content.length(), start + step);
        }
        return result;
    }

    private void increment(Map<String, Integer> map, String key) {
        map.merge(key, 1, Integer::sum);
    }

    private record IngestionStats(int filesScanned, int filesIngested, int filesSkipped,
            Map<String, Integer> skipReasons) {
    }
}
