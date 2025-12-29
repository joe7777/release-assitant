# LLM Host

Service Spring Boot 3.5.x (Java 21) qui orchestre les chats LLM via Spring AI 1.1.2 et les tools MCP.

## RAG retrieval test

L'endpoint `/debug/ragTest` force un appel direct au tool MCP `rag.search` puis (optionnellement) interroge
le LLM **sans tool-calling** pour vérifier qu'il cite les chunks retournés.

### Exemple (recherche seule)

```bash
curl -X POST http://localhost:8082/debug/ragTest \
  -H "Content-Type: application/json" \
  -d '{
    "query": "RestTemplate exchange deprecated",
    "filters": {
      "sourceType": "SPRING_SOURCE",
      "library": "spring-framework",
      "version": "5.3.17",
      "module": "spring-web",
      "repoUrl": "https://github.com/spring-projects/spring-framework",
      "documentKeyPrefix": "SPRING_SOURCE/spring-framework/5.3.17"
    },
    "topK": 6,
    "callLlm": false,
    "maxContextChars": 6000
  }'
```

### Exemple (recherche + LLM)

```bash
curl -X POST http://localhost:8082/debug/ragTest \
  -H "Content-Type: application/json" \
  -d '{
    "query": "RestTemplate exchange deprecated",
    "filters": {
      "sourceType": "SPRING_SOURCE",
      "library": "spring-framework",
      "version": "5.3.17",
      "module": "spring-web"
    },
    "topK": 6,
    "callLlm": true,
    "llmQuestion": "Explique le changement d\'API et où il est utilisé dans mon projet. Cite les sources.",
    "maxContextChars": 6000
  }'
```

### Interpréter la réponse

- `retrieval.results[]` : liste des chunks renvoyés par Qdrant (texte + metadata).
- `llm.used` : indique si un appel LLM a été exécuté.
- `llm.answer` : réponse brute du LLM (sans tool-calling).
- `llm.citationsFound` : documentKey présents dans la réponse LLM.
- `llm.missingCitations` : documentKey manquants (permet de valider que le LLM cite les sources).

### Vérifier les tools MCP disponibles

```bash
curl http://localhost:8082/debug/tools
```

La réponse contient `name` + `description` pour chaque tool, afin de confirmer que `rag.search` est bien exposé.
