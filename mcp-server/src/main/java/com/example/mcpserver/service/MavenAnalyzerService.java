package com.example.mcpserver.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.MavenAnalysisResult;

@Service
public class MavenAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(MavenAnalyzerService.class);

    public MavenAnalysisResult analyze(Path workspace) throws IOException {
        Path pom = workspace.resolve("pom.xml");
        if (!Files.exists(pom)) {
            throw new IOException("pom.xml not found in workspace");
        }

        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (var in = Files.newBufferedReader(pom)) {
            Model model = reader.read(in);
            List<String> springDeps = new ArrayList<>();
            List<String> thirdParty = new ArrayList<>();

            for (Dependency dep : model.getDependencies()) {
                String coord = String.format("%s:%s:%s", dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
                if (dep.getGroupId() != null && dep.getGroupId().startsWith("org.springframework")) {
                    springDeps.add(coord);
                }
                else {
                    thirdParty.add(coord);
                }
            }

            String springBootVersion = detectSpringBoot(model);
            String javaVersion = detectJavaVersion(model);

            return new MavenAnalysisResult(springBootVersion, springDeps, thirdParty, javaVersion);
        }
        catch (XmlPullParserException e) {
            logger.error("Cannot parse pom.xml", e);
            throw new IOException(e);
        }
    }

    private String detectSpringBoot(Model model) {
        if (model.getParent() != null && "spring-boot-starter-parent".equals(model.getParent().getArtifactId())) {
            return model.getParent().getVersion();
        }
        return model.getDependencies().stream()
                .filter(d -> "org.springframework.boot".equals(d.getGroupId()))
                .map(Dependency::getVersion)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse("unknown");
    }

    private String detectJavaVersion(Model model) {
        Properties props = model.getProperties();
        if (props.containsKey("maven.compiler.source")) {
            return props.getProperty("maven.compiler.source");
        }
        if (props.containsKey("java.version")) {
            return props.getProperty("java.version");
        }
        return "unknown";
    }
}
