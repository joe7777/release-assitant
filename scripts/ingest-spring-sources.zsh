#!/usr/bin/env zsh
set -euo pipefail

RAG_BASE_URL=${RAG_BASE_URL:-"http://localhost:8085"}
DRY_RUN=${DRY_RUN:-false}
LOG_DIR=${LOG_DIR:-logs}
VERSIONS_FILE=${VERSIONS_FILE:-"rag_sources/spring_versions.txt"}
MAX_FILES=${MAX_FILES:-""}
FORCE=${FORCE:-false}
INCLUDE_TESTS=${INCLUDE_TESTS:-false}
MODULES=${MODULES:-""}

mkdir -p "${LOG_DIR}"

if [ ! -f "$VERSIONS_FILE" ]; then
  echo "$VERSIONS_FILE introuvable" >&2
  exit 1
fi

timestamp=$(date +%Y%m%d-%H%M%S)
log_file="$LOG_DIR/ingest-spring-${timestamp}.log"

modules_json="null"
if [ -n "$MODULES" ]; then
  modules_json="[\"${MODULES//,/\",\"}\"]"
fi

max_files_json="null"
if [ -n "$MAX_FILES" ]; then
  max_files_json="$MAX_FILES"
fi

while IFS= read -r version; do
  if [[ -z "$version" ]] || [[ "$version" == \#* ]]; then
    continue
  fi

  payload=$(cat <<PAYLOAD
{
  "version": "$version",
  "modules": $modules_json,
  "maxFiles": ${max_files_json},
  "force": ${FORCE},
  "includeTests": ${INCLUDE_TESTS}
}
PAYLOAD
)

  echo "Ingestion Spring Framework $version" | tee -a "$log_file"
  if [ "$DRY_RUN" = "true" ]; then
    echo "Payload:" | tee -a "$log_file"
    echo "$payload" | tee -a "$log_file"
    continue
  fi

  curl -sS -X POST "$RAG_BASE_URL/api/rag/ingest/spring-source" \
    -H 'Content-Type: application/json' \
    -d "$payload" | tee -a "$log_file"
  echo "" >> "$log_file"

done < "$VERSIONS_FILE"
