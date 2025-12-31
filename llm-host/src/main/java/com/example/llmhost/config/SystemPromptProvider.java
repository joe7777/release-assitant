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

    public String buildGuidedCitationsPrompt(int sourceCount, boolean forceCoverage, boolean forceAnyCitation) {
        int minSourcesRequired = Math.max(1, properties.getRag().getCitationMinSourcesRequired());
        double coverageRatio = properties.getRag().getCitationCoverageRatio();
        int minCoverageSources = properties.getRag().getCitationMinSourcesForCoverage();

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("Tu dois répondre uniquement avec les sources.");
        joiner.add("Chaque affirmation doit citer [S#].");
        joiner.add("Si des sources existent, tu n'as pas le droit de répondre \"NON TROUVÉ\" sans expliquer pourquoi elles ne sont pas pertinentes.");
        joiner.add("Cite au moins " + minSourcesRequired + " source(s) ou " + Math.round(coverageRatio * 100) + "% des sources (selon les règles).");
        if (sourceCount >= minCoverageSources) {
            joiner.add("Objectif de couverture: au moins " + Math.round(coverageRatio * 100) + "% des sources.");
        }
        if (forceAnyCitation) {
            joiner.add("Obligation stricte: tu dois citer au moins une source [S#].");
        }
        if (forceCoverage) {
            joiner.add("Tu dois citer au moins " + Math.round(coverageRatio * 100) + "% des sources [S#].");
        }
        return joiner.toString();
    }

    public String buildGuidedUpgradePrompt() {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("Tu es un expert Spring Boot Upgrade Assistant.");
        joiner.add("Tu ne dois parler que de ce qui est dans les sources [S#] et l’inventaire projet S1.");
        joiner.add("Si un impact n’est pas justifié par une source, réponds NON TROUVÉ.");
        joiner.add("Toujours citer [S#] à chaque point.");
        return joiner.toString();
    }
}
