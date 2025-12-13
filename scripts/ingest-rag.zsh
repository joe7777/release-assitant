#!/usr/bin/env zsh
# Script d'ingestion des connaissances pour mcp-knowledge-rag.
# À lancer uniquement en phase d'administration ou de préparation de l'environnement, jamais pendant une analyse en cours.
# L'objectif est de précharger les release notes et guides de migration afin de réduire au maximum les appels OpenAI lors des analyses.
# Exemple d'exécution :
#   export RAG_BASE_URL="http://localhost:8082"
#   export RAG_API_KEY="token-optionnel"
#   DRY_RUN=true ./scripts/ingest-rag.zsh
#   ./scripts/ingest-rag.zsh
set -euo pipefail

RAG_BASE_URL=${RAG_BASE_URL:-""}
RAG_API_KEY=${RAG_API_KEY:-""}
DRY_RUN=${DRY_RUN:-"false"}

function check_prerequisites() {
  if [[ -z "${RAG_BASE_URL}" ]]; then
    echo "Erreur : RAG_BASE_URL doit être défini (ex: http://localhost:8082)." >&2
    exit 1
  fi

  for bin in curl sha256sum python3; do
    if ! command -v "$bin" >/dev/null 2>&1; then
      echo "Erreur : '$bin' est requis pour exécuter ce script." >&2
      exit 1
    fi
  done
}

function build_payload() {
  local sourceType="$1"
  local library="$2"
  local version="$3"
  local content="$4"
  local url="$5"

  python3 - "$sourceType" "$library" "$version" "$content" "$url" <<'PY'
import json
import sys
sourceType, library, version, content, url = sys.argv[1:]
payload = {
    "sourceType": sourceType,
    "library": library,
    "version": version,
    "content": content,
    "url": url,
}
print(json.dumps(payload, ensure_ascii=False))
PY
}

function ingest_document() {
  local sourceType="$1"
  local library="$2"
  local version="$3"
  local content="$4"
  local url="$5"

  local hash
  hash=$(printf "%s" "$content" | sha256sum | awk '{print $1}')
  local size=${#content}

  echo "---"
  echo "Préparation ingestion"
  echo "  sourceType : $sourceType"
  echo "  library    : $library"
  echo "  version    : $version"
  echo "  taille     : ${size} caractères"
  echo "  hash       : $hash"
  echo "  url        : $url"

  local payload
  payload=$(build_payload "$sourceType" "$library" "$version" "$content" "$url")

  if [[ "$DRY_RUN" == "true" ]]; then
    echo "DRY_RUN=true : requête non envoyée. Payload :"
    echo "$payload"
    return 0
  fi

  local headers=("-H" "Content-Type: application/json")
  if [[ -n "$RAG_API_KEY" ]]; then
    headers+=("-H" "Authorization: Bearer $RAG_API_KEY")
  fi

  local response_file
  response_file=$(mktemp)
  local http_code
  http_code=$(curl -sS -o "$response_file" -w "%{http_code}" -X POST "$RAG_BASE_URL/ingest" "${headers[@]}" -d "$payload")

  if [[ "$http_code" == "200" || "$http_code" == "201" ]]; then
    echo "✅ Ingestion réussie (HTTP $http_code)"
  else
    echo "❌ Échec ingestion (HTTP $http_code)"
    cat "$response_file"
    rm -f "$response_file"
    return 1
  fi

  rm -f "$response_file"
}

function ingest_spring_boot_release_notes() {
  ingest_document "SPRING_RELEASE_NOTE" "spring-boot" "2.7-to-3.3" \
    "Synthèse des release notes Spring Boot 2.7.x -> 3.3.x. TODO: ajouter le texte officiel détaillant les changements de compatibilité Jakarta EE, Actuator, Observability et mises à jour de dépendances." \
    "https://docs.spring.io/spring-boot/docs/current/reference/html/"
}

function ingest_spring_security_migration() {
  ingest_document "SPRING_RELEASE_NOTE" "spring-security" "5.x-to-6.x" \
    "Guide de migration Spring Security 5 vers 6 : nouveaux SecurityFilterChain, suppression des WebSecurityConfigurerAdapter, configuration HTTP DSL, mises à jour OAuth2/OIDC. TODO: insérer le guide complet." \
    "https://spring.io/guides/migration/spring-security"
}

function ingest_java_release_notes() {
  ingest_document "JAVA_RELEASE_NOTE" "java" "17-to-21" \
    "Évolutions Java 17 -> 21 : Virtual Threads (Project Loom), améliorations records/switch, pattern matching, API mémoire étrangère, séquenceurs d'empreintes. TODO: compléter avec les release notes officielles." \
    "https://docs.oracle.com/en/java/javase/21/"
}

function main() {
  check_prerequisites
  echo "Ingestion RAG vers ${RAG_BASE_URL} (DRY_RUN=${DRY_RUN})"

  ingest_spring_boot_release_notes
  ingest_spring_security_migration
  ingest_java_release_notes
}

main "$@"
