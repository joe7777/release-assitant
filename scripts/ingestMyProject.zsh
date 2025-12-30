#!/usr/bin/env zsh
set -euo pipefail

RAG_BASE_URL=${RAG_BASE_URL:-"http://localhost:8085"}
DRY_RUN=${DRY_RUN:-false}
LOG_DIR=${LOG_DIR:-logs}
PROJECT_FILE=${PROJECT_FILE:-"rag_sources/my_project.txt"}
MAX_FILES_OVERRIDE=${MAX_FILES:-""}

mkdir -p "${LOG_DIR}"

if [ ! -f "$PROJECT_FILE" ]; then
  echo "$PROJECT_FILE introuvable" >&2
  exit 1
fi

function trim_value() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  echo "$value"
}

function json_escape() {
  local value="$1"
  value=${value//\\/\\\\}
  value=${value//"/\\"}
  value=${value//$'\n'/\\n}
  value=${value//$'\r'/\\r}
  value=${value//$'\t'/\\t}
  echo "$value"
}

typeset -A config
while IFS= read -r line; do
  if [[ -z "$line" ]] || [[ "$line" == \#* ]]; then
    continue
  fi
  if [[ "$line" != *:* ]]; then
    continue
  fi
  key="${line%%:*}"
  value="${line#*:}"
  key=$(trim_value "$key")
  value=$(trim_value "$value")
  config[$key]="$value"
done < "$PROJECT_FILE"

repo_url="${config[repoUrl]:-}"
ref="${config[ref]:-main}"
source_type="${config[sourceType]:-PROJECT_CODE}"
project_key="${config[projectKey]:-}"
max_files="${config[maxFiles]:-}"

if [[ -n "$MAX_FILES_OVERRIDE" ]]; then
  max_files="$MAX_FILES_OVERRIDE"
fi

if [[ -z "$repo_url" ]]; then
  echo "repoUrl manquant dans $PROJECT_FILE" >&2
  exit 1
fi

if [[ -z "$project_key" ]]; then
  project_key=$(basename "$repo_url" .git)
fi

escaped_repo_url=$(json_escape "$repo_url")
escaped_ref=$(json_escape "$ref")
escaped_project_key=$(json_escape "$project_key")
escaped_source_type=$(json_escape "$source_type")

max_files_json="null"
if [[ -n "$max_files" ]]; then
  max_files_json="$max_files"
fi

timestamp=$(date +%Y%m%d-%H%M%S)
log_file="$LOG_DIR/ingest-project-${timestamp}.log"

payload=$(cat <<PAYLOAD
{
  "repoUrl": "$escaped_repo_url",
  "ref": "$escaped_ref",
  "projectKey": "$escaped_project_key",
  "sourceType": "$escaped_source_type",
  "includeTests": false,
  "includeNonJava": false,
  "includeKotlin": false,
  "maxFiles": $max_files_json
}
PAYLOAD
)

echo "Ingestion projet $project_key ($repo_url@$ref)" | tee -a "$log_file"
if [ "$DRY_RUN" = "true" ]; then
  echo "Payload:" | tee -a "$log_file"
  echo "$payload" | tee -a "$log_file"
  exit 0
fi

curl -sS -X POST "$RAG_BASE_URL/api/rag/ingest/project" \
  -H 'Content-Type: application/json' \
  -d "$payload" | tee -a "$log_file"
echo "" >> "$log_file"
