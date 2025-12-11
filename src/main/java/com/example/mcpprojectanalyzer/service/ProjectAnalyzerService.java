package com.example.mcpprojectanalyzer.service;

import com.example.mcpprojectanalyzer.api.AnalyzeRequest;
import com.example.mcpprojectanalyzer.api.AnalyzeResponse;
import com.example.mcpprojectanalyzer.api.DependencyDto;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProjectAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(ProjectAnalyzerService.class);

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("mcp-project-analyzer-");
            log.info("Cloning repository {} into {}", request.repoUrl(), tempDir);
            cloneRepository(request, tempDir);

            List<Path> pomFiles = findPomFiles(tempDir);
            List<DependencyDto> dependencies = new ArrayList<>();
            Set<String> modules = new LinkedHashSet<>();
            String springVersion = null;

            for (Path pomFile : pomFiles) {
                try (Reader reader = new InputStreamReader(Files.newInputStream(pomFile))) {
                    Model model = new MavenXpp3Reader().read(reader);
                    modules.addAll(model.getModules());
                    dependencies.addAll(mapDependencies(model.getDependencies()));
                    if (springVersion == null) {
                        springVersion = detectSpringBootVersion(model);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse pom {}", pomFile, e);
                }
            }

            // TODO: Add a Maven invocation (e.g., dependency:tree) for more accurate dependency resolution.

            return new AnalyzeResponse(
                    springVersion,
                    dependencies,
                    modules.stream().toList()
            );
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Failed to analyze repository", e);
        } finally {
            if (tempDir != null) {
                try {
                    FileSystemUtils.deleteRecursively(tempDir);
                } catch (IOException e) {
                    log.warn("Failed to clean temporary directory {}", tempDir, e);
                }
            }
        }
    }

    private void cloneRepository(AnalyzeRequest request, Path destination) throws GitAPIException {
        var cloneCommand = Git.cloneRepository()
                .setURI(request.repoUrl())
                .setDirectory(destination.toFile());

        if (request.branch() != null && !request.branch().isBlank()) {
            cloneCommand.setBranch(request.branch());
        }
        if (request.gitToken() != null && !request.gitToken().isBlank()) {
            cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(request.gitToken(), ""));
        }

        cloneCommand.call();
    }

    private List<Path> findPomFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("pom.xml"))
                    .collect(Collectors.toList());
        }
    }

    private List<DependencyDto> mapDependencies(List<Dependency> mavenDependencies) {
        return mavenDependencies.stream()
                .map(dep -> new DependencyDto(
                        dep.getGroupId(),
                        dep.getArtifactId(),
                        dep.getVersion(),
                        dep.getScope()
                ))
                .toList();
    }

    private String detectSpringBootVersion(Model model) {
        Parent parent = model.getParent();
        if (parent != null && "org.springframework.boot".equals(parent.getGroupId())
                && "spring-boot-starter-parent".equals(parent.getArtifactId())) {
            return parent.getVersion();
        }

        if (model.getProperties() != null && model.getProperties().containsKey("spring-boot.version")) {
            return model.getProperties().getProperty("spring-boot.version");
        }

        return null;
    }
}
