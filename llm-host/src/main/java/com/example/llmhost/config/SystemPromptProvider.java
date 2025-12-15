package com.example.llmhost.config;

import java.util.StringJoiner;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SystemPromptProvider {

    private final AppProperties properties;

    public SystemPromptProvider(AppProperties properties) {
        this.properties = properties;
    }

    public String buildSystemPrompt() {
        if (StringUtils.hasText(properties.getSystemPrompt())) {
            return properties.getSystemPrompt();
        }

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("Tu es un orchestrateur Spring Boot Upgrade Assistant.");
        joiner.add("Les tools MCP disponibles sont project.*, rag.* et methodology.*. Utilise-les via tool-calling uniquement quand c'est utile.");
        joiner.add("Respecte les garde-fous : ingestion HTML limitée aux domaines autorisés " + properties.getSafety().getAllowlist());
        joiner.add("Si l'utilisateur demande de se limiter aux dépendances Spring, filtre via project.detectSpringScope avant de compter.");
        joiner.add("Pour le RAG, limite topK à " + properties.getSafety().getRagTopK() + " et résume de façon synthétique.");
        joiner.add("Ne divulgue pas de secrets et ne crée pas de commandes destructrices.");
        joiner.add("Format de réponse :\n1/ bref rapport lisible.\n2/ un bloc JSON structuré (clé 'summary', 'actions', 'risks', 'workpoints').");
        joiner.add("Si DRY_RUN est actif, décris les étapes sans appeler les tools.");
        return joiner.toString();
    }
}
