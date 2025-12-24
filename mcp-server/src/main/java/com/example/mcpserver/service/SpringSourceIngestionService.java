package com.example.mcpserver.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.SpringSourceIngestionResponse;

@Service
public class SpringSourceIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(SpringSourceIngestionService.class);
    private static final String REPO_URL = "https://github.com/spring-projects/spring-framework";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".java", ".kt", ".kts");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w\\.]+)\\s*;?");
    private static final Pattern JAVADOC_PATTERN = Pattern.compile("/\\*\\*.*?\\*/", Pattern.DOTALL);

    private final VectorStore vectorStore;
    private final HashingService hashingService;
    private final IngestionLedger ingestionLedger;
    private final Path cacheRoot;
    private final int maxFileSizeBytes;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int defaultMaxFiles;
    private final boolean defaultIncludeTests;

    public SpringSourceIngestionService(VectorStore vectorStore, HashingService hashingService,
            IngestionLedger ingestionLedger,
            @Value("${mcp.spring-source.cache-root:spring-sources}") String cacheRoot,
            @Value("${mcp.rag.max-content-length:1048576}") int maxFileSizeBytes,
            @Value("${mcp.rag.chunk-size:800}") int chunkSize,
            @Value("${mcp.rag.chunk-overlap:80}") int chunkOverlap,
            @Value("${mcp.spring-source.default-max-files:2000}") int defaultMaxFiles,
            @Value("${mcp.spring-source.include-tests:false}") boolean defaultIncludeTests) throws IOException {
        this.vectorStore = vectorStore;
        this.hashingService = hashingService;
        this.ingestionLedger = ingestionLedger;
        this.cacheRoot = Path.of(cacheRoot).toAbsolutePath();
        Files.createDirectories(this.cacheRoot);
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.defaultMaxFiles = defaultMaxFiles;
        this.defaultIncludeTests = defaultIncludeTests;
    }

    public SpringSourceIngestionResponse ingestSpringSource(String version, List<String> modules, String tagOrBranch,
            Boolean includeJavadoc, Integer maxFiles, Boolean force, Boolean includeTests)
            throws IOException, GitAPIException {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        boolean includeJavadocResolved = Boolean.TRUE.equals(includeJavadoc);
        boolean forceIngest = Boolean.TRUE.equals(force);
        boolean includeTestsResolved = includeTests != null ? includeTests : defaultIncludeTests;
        int maxFilesResolved = maxFiles != null ? maxFiles : defaultMaxFiles;
        String ref = resolveRef(version, tagOrBranch);

        Path repoPath = ensureRepo(REPO_URL);
        String commit;
        try (Git git = Git.open(repoPath.toFile())) {
            fetch(git);
            checkoutRef(git, ref);
            commit = git.getRepository().resolve("HEAD").name();
        }

        return ingestFromRepo(repoPath, version, ref, commit, modules, includeJavadocResolved, maxFilesResolved,
                forceIngest, includeTestsResolved);
    }

    private SpringSourceIngestionResponse ingestFromRepo(Path repoPath, String version, String ref, String commit,
            List<String> modules, boolean includeJavadoc, int maxFiles, boolean forceIngest, boolean includeTests)
            throws IOException {
        Set<String> moduleSet = modules == null ? Set.of()
                : modules.stream().filter(m -> m != null && !m.isBlank()).collect(HashSet::new, Set::add, Set::addAll);
        List<String> warnings = new ArrayList<>();
        int filesScanned = 0;
        int filesIngested = 0;
        int filesSkipped = 0;
        int chunksStored = 0;
        int chunksSkipped = 0;

        try (Stream<Path> stream = Files.walk(repoPath)) {
            var iterator = stream.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (filesScanned >= maxFiles) {
                    warnings.add("maxFiles limit reached (" + maxFiles + ")");
                    break;
                }
                filesScanned++;
                if (!isAllowedSource(file)) {
                    filesSkipped++;
                    continue;
                }
                if (!includeTests && isTestPath(file)) {
                    filesSkipped++;
                    continue;
                }
                String relativePath = repoPath.relativize(file).toString().replace("\\", "/");
                String module = extractModule(relativePath);
                if (!moduleSet.isEmpty() && !moduleSet.contains(module)) {
                    filesSkipped++;
                    continue;
                }
                if (isBinary(file)) {
                    filesSkipped++;
                    continue;
                }

                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (!includeJavadoc) {
                    content = stripJavadoc(content);
                }
                if (content.isBlank()) {
                    filesSkipped++;
                    continue;
                }

                String documentKey = buildDocumentKey(version, module, relativePath);
                String documentHash = hashingService.sha256("SPRING_SOURCE|spring-framework|" + version + "|"
                        + relativePath + "|" + content);
                if (!forceIngest && ingestionLedger.alreadyIngested(documentHash)) {
                    filesSkipped++;
                    continue;
                }

                List<String> chunks = chunk(content, chunkSize, chunkOverlap);
                List<Document> documents = new ArrayList<>();
                int chunkIndex = 0;
                String packageName = extractPackageName(content);
                String className = extractClassName(file);
                for (String chunk : chunks) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("sourceType", "SPRING_SOURCE");
                    metadata.put("library", "spring-framework");
                    metadata.put("version", version);
                    metadata.put("repoUrl", REPO_URL);
                    metadata.put("commit", commit);
                    metadata.put("module", module);
                    metadata.put("filePath", relativePath);
                    metadata.put("className", className);
                    metadata.put("packageName", packageName);
                    metadata.put("documentKey", documentKey);
                    metadata.put("documentHash", documentHash);
                    metadata.put("chunkTextHash", hashingService.sha256(documentHash + chunk));
                    metadata.put("chunkIndex", chunkIndex++);
                    documents.add(new Document(chunk, metadata));
                }
                if (documents.isEmpty()) {
                    filesSkipped++;
                    continue;
                }
                vectorStore.add(documents);
                ingestionLedger.record(documentHash);
                filesIngested++;
                chunksStored += documents.size();
            }
        }
        return new SpringSourceIngestionResponse(version, ref, commit, filesScanned, filesIngested, filesSkipped,
                chunksStored, chunksSkipped, warnings);
    }

    private Path ensureRepo(String repoUrl) throws GitAPIException {
        if (!REPO_URL.equals(repoUrl)) {
            throw new IllegalArgumentException("Repository not allowed: " + repoUrl);
        }
        Path repoPath = cacheRoot.resolve("spring-framework");
        if (Files.exists(repoPath.resolve(".git"))) {
            return repoPath;
        }
        logger.info("Cloning Spring Framework repository into {}", repoPath);
        Git.cloneRepository().setURI(repoUrl).setDirectory(repoPath.toFile()).call().close();
        return repoPath;
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

    private String resolveRef(String version, String tagOrBranch) {
        if (tagOrBranch != null && !tagOrBranch.isBlank()) {
            return tagOrBranch;
        }
        if (version.toLowerCase(Locale.ROOT).startsWith("v")) {
            return version;
        }
        return "v" + version;
    }

    private boolean isAllowedSource(Path file) {
        String name = file.toString().toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private boolean isTestPath(Path file) {
        String path = file.toString().replace("\\", "/");
        return path.contains("/src/test/") || path.contains("/src/testFixtures/");
    }

    private boolean isBinary(Path p) {
        try {
            if (Files.size(p) > maxFileSizeBytes) {
                return true;
            }
            String probe = Files.probeContentType(p);
            if (probe != null && !probe.startsWith("text") && !probe.contains("xml")) {
                return true;
            }
            byte[] bytes = Files.readAllBytes(p);
            for (byte b : bytes) {
                if (b == 0) {
                    return true;
                }
            }
            return false;
        }
        catch (IOException e) {
            logger.warn("Unable to inspect file {}", p, e);
            return true;
        }
    }

    private String extractModule(String relativePath) {
        int idx = relativePath.indexOf('/');
        return idx > 0 ? relativePath.substring(0, idx) : relativePath;
    }

    private String buildDocumentKey(String version, String module, String relativePath) {
        return "SPRING_SOURCE/spring-framework/" + version + "/" + module + "/" + relativePath;
    }

    private String extractClassName(Path file) {
        String name = file.getFileName().toString();
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

    private String stripJavadoc(String content) {
        return JAVADOC_PATTERN.matcher(content).replaceAll("");
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
}
