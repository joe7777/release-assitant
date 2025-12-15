#!/usr/bin/env zsh
set -euo pipefail

RAG_BASE_URL=${RAG_BASE_URL:-"http://localhost:8085"}
DRY_RUN=${DRY_RUN:-false}
LOG_DIR=${LOG_DIR:-logs}
SOURCES_FILE=${SOURCES_FILE:-"rag_sources/sources.csv"}
mkdir -p "${LOG_DIR}"

if [ ! -f "$SOURCES_FILE" ]; then
  echo "$SOURCES_FILE introuvable" >&2
  exit 1
fi

timestamp=$(date +%Y%m%d-%H%M%S)
log_file="$LOG_DIR/ingest-${timestamp}.log"

while IFS=';' read -r sourceType library version url docId _ _; do
  if [[ -z "$sourceType" ]] || [[ "$sourceType" = "sourceType" ]] || [[ "$sourceType" == \#* ]]; then
    continue
  fi
  payload=$(cat <<PAYLOAD
{
  "sourceType": "$sourceType",
  "library": "$library",
  "version": "$version",
  "url": "$url",
  "docId": "$docId"
}
PAYLOAD
)

  echo "Ingestion $url" | tee -a "$log_file"
  if [ "$DRY_RUN" = "true" ]; then
    echo "Payload:" | tee -a "$log_file"
    echo "$payload" | tee -a "$log_file"
    continue
  fi

  curl -sS -X POST "$RAG_BASE_URL/api/rag/ingest/html" \
    -H 'Content-Type: application/json' \
    -d "$payload" | tee -a "$log_file"
  echo "" >> "$log_file"

done < "$SOURCES_FILE"
