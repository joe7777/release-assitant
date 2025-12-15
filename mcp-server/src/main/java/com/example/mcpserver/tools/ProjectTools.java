package com.example.mcpserver.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.example.mcpserver.dto.CloneResponse;
import com.example.mcpserver.dto.IndexRequestOptions;
import com.example.mcpserver.dto.IndexResponse;
import com.example.mcpserver.dto.MavenAnalysisResult;
import com.example.mcpserver.service.CodeIndexer;
import com.example.mcpserver.service.MavenAnalyzerService;
import com.example.mcpserver.service.WorkspaceService;

@Component
public class ProjectTools {

    private final WorkspaceService workspaceService;
    private final MavenAnalyzerService mavenAnalyzerService;
    private final CodeIndexer codeIndexer;

    public ProjectTools(WorkspaceService workspaceService, MavenAnalyzerService mavenAnalyzerService,
            CodeIndexer codeIndexer) {
        this.workspaceService = workspaceService;
        this.mavenAnalyzerService = mavenAnalyzerService;
        this.codeIndexer = codeIndexer;
    }

    @Tool(name = "project.clone", description = "Clone un dépôt Git dans un workspace local")
    public CloneResponse cloneRepository(String repoUrl, String branch, String authRef) throws GitAPIException, IOException {
        String workspaceId = workspaceService.cloneRepository(repoUrl, branch, authRef);
        Path path = workspaceService.resolveWorkspace(workspaceId);
        return new CloneResponse(workspaceId, path.toString());
    }

    @Tool(name = "project.analyzeMaven", description = "Analyse le pom.xml et les dépendances Maven")
    public MavenAnalysisResult analyzeMaven(String workspaceId) throws IOException {
        Path workspace = workspaceService.resolveWorkspace(workspaceId);
        return mavenAnalyzerService.analyze(workspace);
    }

    @Tool(name = "project.indexCodeToRag", description = "Indexe le code source dans Qdrant avec embeddings")
    public IndexResponse indexCodeToRag(String workspaceId, IndexRequestOptions options) throws IOException {
        Path workspace = workspaceService.resolveWorkspace(workspaceId);
        if (options == null) {
            options = new IndexRequestOptions(800, 80, true);
        }
        Map<String, String> metadata = new HashMap<>();
        metadata.put("workspaceId", workspaceId);
        metadata.put("collection", "mcp_documents");
        return codeIndexer.indexWorkspace(workspace, options, metadata);
    }

    @Tool(name = "project.detectSpringScope", description = "Filtre les dépendances Spring")
    public List<String> detectSpringScope(List<String> dependencies) {
        return dependencies.stream().filter(d -> d.contains("spring") || d.contains("Spring"))
                .collect(Collectors.toList());
    }
}
