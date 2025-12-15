# Spring Boot Upgrade Assistant

Spring Boot Upgrade Assistant aide les équipes à analyser un dépôt Git et à estimer l'effort de migration d'une application Spring Boot vers une version cible (par exemple de 2.7.x vers 3.3.x). L'application orchestre une SPA React, un backend Spring Boot 3.x et des serveurs MCP outillés par un LLM afin de générer un rapport complet : changements attendus, impacts de code, vulnérabilités et workpoints.

## Architecture globale
```
+-------------+        +---------------------+
|   Frontend  | <----> |   Backend API (SB)  |----> PostgreSQL
| React SPA   |        | controllers & core  |
+-------------+        +----------+----------+
                               |           |
                               v           v
                     +----------------+   +----------------+
                     |  MCP Servers   |   |   Qdrant RAG   |
                     | (analyzer,     |   | vector store   |
                     |  knowledge,    |   +----------------+
                     |  methodology)  |
                     +----------------+
                               ^
                               |
                        +-------------+
                        |  LLM Host   |
                        | (Spring AI) |
                        +-------------+
```
- **Frontend (React/Vite)** : saisie d'un prompt libre et consultation de la réponse outillée.
- **Backend (Spring Boot 3, Java 21)** : API REST historique (analyses), logique métier et persistence Postgres.
- **LLM Host (Spring Boot 3.5 + Spring AI 1.1.2)** : reçoit un prompt, orchestre le modèle (Ollama ou OpenAI) et appelle les tools MCP via tool-calling.
- **Serveurs MCP** :
  - `mcp-project-analyzer` pour explorer le repo, les dépendances Maven et la version actuelle de Spring Boot.
  - `mcp-knowledge-rag` pour interroger la base vectorielle (Qdrant) avec les release notes, CVE et documentation.
  - `mcp-methodology` pour appliquer le calcul de workpoints.
  - `mcp-server` (MCP Streamable HTTP) expose les tools unifiés (méthodologie, analyse Maven, ingestion/search RAG) sans encore
    activer de tool-calling LLM.
- **Bases de données** : PostgreSQL pour l'état des analyses, Qdrant pour le RAG.

## Prérequis
- Docker et Docker Compose
- Java 21 et Maven 3.9+
- Node.js 18+ et npm
- Variable d'environnement `OPENAI_API_KEY` uniquement si vous activez le provider OpenAI (profil `prod`).

## Fournisseurs LLM/Embeddings (Spring AI)
- Abstraction unique via `ChatGateway` et `EmbeddingGateway`, implémentée avec Spring AI.
- Provider **par défaut** : Ollama (modèle chat `llama3.1:8b`, embeddings `nomic-embed-text`).
- Provider **prod** : OpenAI (`gpt-4o-mini` par défaut, embeddings `text-embedding-3-small`).

### Service `llm-host`
- Endpoint REST : `POST /chat` (ou `/runs`) avec `{ "prompt": "...", "dryRun": false }`.
- Tool-calling MCP : project.*, rag.*, methodology.* exposés par `mcp-server`.
- Traces : chaque réponse expose la liste des tool-calls (nom, durée, arguments). DRY_RUN force la planification sans exécution des tools.

### Local LLM (Ollama)
1. Démarrer via Docker Compose (inclut `ollama` et le service `llm-host`)
   ```bash
   docker-compose up --build
   ```
2. Télécharger les modèles nécessaires (obligatoire au premier démarrage) :
   ```bash
   docker-compose exec ollama ollama pull llama3.1:8b
   docker-compose exec ollama ollama pull nomic-embed-text
   ```
3. Le profil `local` ou la propriété `APP_AI_PROVIDER=ollama` sélectionnent automatiquement les endpoints Ollama. `llm-host` écoute sur `http://localhost:8082`.

### Production (OpenAI)
1. Exporter la clé API :
   ```bash
   export OPENAI_API_KEY="sk-..."
   ```
2. Activer le profil `prod` ou définir `APP_AI_PROVIDER=openai`.
3. Les modèles peuvent être personnalisés via `OPENAI_CHAT_MODEL` et `OPENAI_EMBEDDING_MODEL`.

### Basculer de provider
- Par propriété : `APP_AI_PROVIDER=ollama|openai` (par défaut : `ollama`).
- Par profil Spring : `SPRING_PROFILES_ACTIVE=local|prod`.
- Les configurations sont regroupées dans `application.yml` + overlays `application-local.yml` et `application-prod.yml`.

Exemple de prompt côté UI/llm-host :
```
Voici le lien de mon projet git https://github.com/xxx/yyy. Analyse les release notes Spring Boot 3.3.3 et donne-moi un rapport,
limite-toi aux dépendances Spring et propose des workpoints.
```

## Build et exécution locale
1. **LLM Host**
   ```bash
   cd llm-host
   mvn clean package
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```
   Endpoint de chat : `http://localhost:8082/chat`.

2. **Backend** (optionnel si seul le prompt est utilisé)
   ```bash
   cd backend
   mvn clean package
   mvn spring-boot:run
   ```
   Par défaut, l'API historique écoute sur `http://localhost:8080`.

3. **Frontend**
   ```bash
   cd frontend
   npm install
   npm run build
   npm run dev -- --host --port 3000
   ```
   L'interface est disponible sur `http://localhost:3000` et cible `llm-host` via `VITE_LLM_HOST_URL`.

## Démarrage avec Docker Compose
1. Exporter la clé OpenAI :
   ```bash
   export OPENAI_API_KEY="sk-..."
   ```
2. Construire et lancer l'ensemble (backend, frontend, Postgres, Qdrant, MCP) :
   ```bash
   docker-compose up --build
   ```
3. Points d'accès :
   - Frontend : http://localhost:3000
   - Backend : http://localhost:8080
   - Qdrant : http://localhost:6333
  - MCP Server : http://localhost:8085 (transport Streamable HTTP `/mcp`)
   - PostgreSQL : localhost:5432 (db `upgrader`, utilisateur/mot de passe `upgrader`)

## Accéder à la documentation Swagger UI
- **Backend (API principale)** : http://localhost:8080/swagger-ui/index.html
- **MCP Knowledge RAG** : http://localhost:8081/swagger-ui/index.html
- Une fois les services démarrés (via Maven ou Docker Compose), ces URLs exposent le catalogue OpenAPI pour tester les endpoints et vérifier la configuration.

## Ingestion des connaissances RAG (offline)
- Le script `scripts/ingest-baseline.zsh` lit `rag_sources/sources.csv` (séparateur `;`) et appelle l'API REST interne du `mcp-server` (`/api/rag/ingest/html`).
- L'endpoint est idempotent grâce aux métadonnées `documentHash` et `chunkHash` : une source déjà ingérée est ignorée.
- Variables attendues :
  - `RAG_BASE_URL` (ex: `http://localhost:8085` via docker-compose)
  - `DRY_RUN` (`true/false`, affiche les payloads sans ingestion)

Exemple d'exécution :
```bash
export RAG_BASE_URL="http://localhost:8085"
DRY_RUN=true ./scripts/ingest-baseline.zsh   # vérifie les payloads
./scripts/ingest-baseline.zsh                # ingère réellement dans Qdrant
```

## Tester le MCP server (sans LLM)
- Démarrer via Docker Compose (`mcp-server` écoute sur `8085`).
- Les tools sont exposés via le transport Streamable HTTP `/mcp` et via des endpoints REST pratiques :
  - `POST /api/rag/ingest/html` pour ingérer une page.
  - `POST /api/rag/ingest/text` pour injecter un contenu brut.
  - `POST /api/rag/search` pour interroger Qdrant.
- Les outils `methodology.*` et `project.*` sont déclarés avec `@McpTool` et scannés automatiquement grâce à Spring AI 1.1.2.

## Endpoints principaux du backend
- `POST /analyses` : lancer une nouvelle analyse à partir d'un repo et d'une version cible.
- `GET /analyses` : lister les analyses déjà enregistrées.
- `GET /analyses/{id}` : consulter le détail d'une analyse.

## Flux fonctionnel type
1. L'utilisateur saisit l'URL du dépôt Git, un token d'accès si nécessaire et la version cible de Spring Boot depuis la SPA.
2. Le backend transmet au LLM un prompt orchestrant les appels aux serveurs MCP.
3. Les MCP analysent le dépôt, interrogent le RAG et appliquent la méthodologie de calcul des workpoints.
4. Le LLM renvoie un JSON structuré avec les changements, impacts de code, vulnérabilités et points d'effort.
5. Le backend persiste les résultats, et l'UI expose un rapport de migration.

## Travaux futurs
- Intégration complète des flux MCP côté LLM pour une exécution de bout en bout.
- UI plus avancée (historique, filtres, export PDF/HTML).
- Automatisation de l'ingestion des release notes et CVE dans Qdrant.
- Hooks CI pour analyser automatiquement les pull requests de migration.
