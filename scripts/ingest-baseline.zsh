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

while IFS= read -r line || [[ -n "$line" ]]; do
  fields=("${(@s:;:)line}")
  sourceType=${fields[1]-""}
  library=${fields[2]-""}
  version=${fields[3]-""}
  url=${fields[4]-""}
  docId=${fields[5]-""}
  if (( ${#fields[@]} >= 8 )); then
    docKind=${fields[6]-""}
  else
    docKind=""
  fi

  if [[ -z "$sourceType" ]] || [[ "$sourceType" = "sourceType" ]] || [[ "$sourceType" == \#* ]]; then
    continue
  fi
  if [[ -z "$docKind" ]]; then
    if [[ "$sourceType" == "SPRING_RELEASE_NOTE" ]]; then
      docKind="RELEASE_NOTES"
    else
      docKind="DOC"
    fi
  fi
  payload=$(cat <<PAYLOAD
{
  "sourceType": "$sourceType",
  "library": "$library",
  "version": "$version",
  "url": "$url",
  "docId": "$docId",
  "docKind": "$docKind"
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
