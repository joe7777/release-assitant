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
```
- **Frontend (React/Vite)** : saisie de l'URL du repo, du token, de la version cible et consultation des résultats.
- **Backend (Spring Boot 3, Java 21)** : API REST, logique métier et persistence Postgres.
- **Serveurs MCP** :
  - `mcp-project-analyzer` pour explorer le repo, les dépendances Maven et la version actuelle de Spring Boot.
  - `mcp-knowledge-rag` pour interroger la base vectorielle (Qdrant) avec les release notes, CVE et documentation.
  - `mcp-methodology` pour appliquer le calcul de workpoints.
- **Bases de données** : PostgreSQL pour l'état des analyses, Qdrant pour le RAG.

## Prérequis
- Docker et Docker Compose
- Java 21 et Maven 3.9+
- Node.js 18+ et npm
- Variable d'environnement `OPENAI_API_KEY` pour le LLM

## Build et exécution locale
1. **Backend**
   ```bash
   cd backend
   mvn clean package
   mvn spring-boot:run
   ```
   Par défaut, l'API écoute sur `http://localhost:8080`.

2. **Frontend**
   ```bash
   cd frontend
   npm install
   npm run build
   npm run dev -- --host --port 3000
   ```
   L'interface est disponible sur `http://localhost:3000`.

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
   - PostgreSQL : localhost:5432 (db `upgrader`, utilisateur/mot de passe `upgrader`)

## Ingestion des connaissances RAG (offline)
- Le script `scripts/ingest-rag.zsh` prépare les release notes et guides de migration pour `mcp-knowledge-rag` (POST `/ingest`).
- L'endpoint `/ingest` est idempotent : chaque document est identifié par un hash SHA-256 calculé à partir du couple (sourceType, library, version) et du contenu normalisé. Un document déjà présent est ignoré, ce qui évite les doublons et limite les appels d'embeddings OpenAI/Qdrant.
- Il s'exécute hors analyse pour éviter tout impact sur les calculs en cours et réduire les appels à OpenAI.
- Variables attendues :
  - `RAG_BASE_URL` (ex: `http://localhost:8082` via docker-compose)
  - `RAG_API_KEY` (optionnel si l'API est protégée)
  - `DRY_RUN` (`true/false`, permet d'afficher les payloads sans appeler l'API)

Exemple d'exécution :
```bash
export RAG_BASE_URL="http://localhost:8082"
export RAG_API_KEY="token-optionnel" # optionnel
DRY_RUN=true ./scripts/ingest-rag.zsh   # vérifie les payloads
./scripts/ingest-rag.zsh                # ingère réellement dans Qdrant
```

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
