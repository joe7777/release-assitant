package com.example.mcpanalyzer.service;

import com.example.mcpanalyzer.api.dto.AnalyzeRequest;
import com.example.mcpanalyzer.api.dto.AnalyzeResponse;
import com.example.mcpanalyzer.api.dto.DependencyDto;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProjectAnalyzerService {

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        Path workingDirectory = null;
        try {
            workingDirectory = Files.createTempDirectory("mcp-analyzer-");
            Path repoDir = cloneRepository(request, workingDirectory);
            switchBranchIfNeeded(request, repoDir);

            List<String> modules = detectModules(repoDir);
            String springBootVersion = detectSpringBootVersion(repoDir);
            Path dotFile = runDependencyTree(repoDir);
            List<DependencyDto> dependencies = extractDependencies(dotFile);

            return new AnalyzeResponse(springBootVersion, dependencies, modules);
        } catch (IOException | InterruptedException | XmlPullParserException e) {
            throw new IllegalStateException("Failed to analyze repository", e);
        } finally {
            if (workingDirectory != null) {
                FileSystemUtils.deleteRecursively(workingDirectory.toFile());
            }
        }
    }

    private Path cloneRepository(AnalyzeRequest request, Path workingDirectory) throws GitCloneException {
        try {
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(request.getRepoUrl())
                    .setDirectory(workingDirectory.toFile());

            if (request.getGitToken() != null && !request.getGitToken().isBlank()) {
                cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(request.getGitToken(), ""));
            }

            try (Git git = cloneCommand.call()) {
                return git.getRepository().getWorkTree().toPath();
            }
        } catch (Exception e) {
            throw new GitCloneException("Unable to clone repository", e);
        }
    }

    private void switchBranchIfNeeded(AnalyzeRequest request, Path repoDir) throws GitCheckoutException {
        if (request.getBranch() == null || request.getBranch().isBlank()) {
            return;
        }
        String branchName = request.getBranch();
        if (!branchName.startsWith("refs/")) {
            branchName = "refs/heads/" + branchName;
        }
        try (Git git = Git.open(repoDir.toFile())) {
            CheckoutCommand checkoutCommand = git.checkout().setName(branchName).setForce(true);
            checkoutCommand.call();
        } catch (Exception e) {
            throw new GitCheckoutException("Unable to checkout branch " + request.getBranch(), e);
        }
    }

    private Path runDependencyTree(Path repoDir) throws IOException, InterruptedException {
        Path dotFile = repoDir.resolve("dependency-tree.dot");
        ProcessBuilder processBuilder = new ProcessBuilder("mvn", "-q", "dependency:tree", "-DoutputType=dot",
                "-DoutputFile=" + dotFile.toString());
        processBuilder.directory(repoDir.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Maven dependency:tree failed with code " + exitCode + "\n" + output);
        }
        return dotFile;
    }

    private List<DependencyDto> extractDependencies(Path dotFile) throws IOException {
        List<DependencyDto> dependencies = new ArrayList<>();
        if (!Files.exists(dotFile)) {
            return dependencies;
        }
        Pattern labelPattern = Pattern.compile("label=\\\"([^\\\"]+)\\\"");
        Set<String> seen = new HashSet<>();
        for (String line : Files.readAllLines(dotFile)) {
            Matcher matcher = labelPattern.matcher(line);
            if (matcher.find()) {
                String coordinate = matcher.group(1);
                if (seen.contains(coordinate)) {
                    continue;
                }
                seen.add(coordinate);
                String[] parts = coordinate.split(":");
                if (parts.length >= 5) {
                    dependencies.add(new DependencyDto(parts[0], parts[1], parts[3], parts[4]));
                } else {
                    // TODO: handle other formats returned by dependency:tree
                }
            }
        }
        return dependencies;
    }

    private List<String> detectModules(Path repoDir) throws IOException, XmlPullParserException {
        Path pom = repoDir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(pom)) {
            MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
            Model model = xpp3Reader.read(reader);
            return new ArrayList<>(model.getModules());
        }
    }

    private String detectSpringBootVersion(Path repoDir) throws IOException, XmlPullParserException {
        Path pom = repoDir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return "unknown";
        }
        try (Reader reader = Files.newBufferedReader(pom)) {
            MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
            Model model = xpp3Reader.read(reader);
            Parent parent = model.getParent();
            if (parent != null && "org.springframework.boot".equals(parent.getGroupId())
                    && ("spring-boot-starter-parent".equals(parent.getArtifactId()) || "spring-boot-dependencies".equals(parent.getArtifactId()))) {
                return parent.getVersion();
            }
            Optional<String> managed = Optional.ofNullable(model.getDependencyManagement())
                    .flatMap(dm -> dm.getDependencies().stream()
                            .filter(dep -> isSpringBootBom(dep))
                            .map(Dependency::getVersion)
                            .findFirst());
            if (managed.isPresent()) {
                return managed.get();
            }
        }
        return "unknown";
    }

    private boolean isSpringBootBom(Dependency dependency) {
        return dependency != null
                && "org.springframework.boot".equals(dependency.getGroupId())
                && "spring-boot-dependencies".equals(dependency.getArtifactId());
    }

    private static class GitCloneException extends RuntimeException {
        GitCloneException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class GitCheckoutException extends RuntimeException {
        GitCheckoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
