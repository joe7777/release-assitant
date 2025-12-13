#!/usr/bin/env zsh
# Script d'ingestion hors-ligne des pages HTML dans mcp-knowledge-rag via l'API /ingestFromHtml.
# Utilisation :
#   DRY_RUN=true ./scripts/ingest-from-html.zsh
#   RAG_BASE_URL="http://localhost:8082" RAG_API_KEY="..." ./scripts/ingest-from-html.zsh
set -euo pipefail

RAG_BASE_URL=${RAG_BASE_URL:-"http://localhost:8082"}
RAG_API_KEY=${RAG_API_KEY:-""}
DRY_RUN=${DRY_RUN:-"false"}
MAX_CHARS=${MAX_CHARS:-"200000"}
DEFAULT_SOURCE_TYPE=${DEFAULT_SOURCE_TYPE:-"SPRING_RELEASE_NOTE"}
DEFAULT_LIBRARY=${DEFAULT_LIBRARY:-"spring-boot"}
DEFAULT_VERSION=${DEFAULT_VERSION:-"latest"}
SELECTOR_CONTENT_CSS=${SELECTOR_CONTENT_CSS:-""}
SELECTOR_REMOVE_CSS=${SELECTOR_REMOVE_CSS:-""}
SOURCES_FILE=${SOURCES_FILE:-"rag_sources/sources.tsv"}
LOG_FILE=${LOG_FILE:-"logs/ingest-from-html.log"}
RESULTS_FILE=${RESULTS_FILE:-"logs/ingest-from-html-results.jsonl"}
FAIL_FAST=${FAIL_FAST:-"false"}

typeset -gA CURRENT_LINE
HAS_JQ="false"
if command -v jq >/dev/null 2>&1; then
  HAS_JQ="true"
fi

function require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Erreur : la commande requise '$cmd' est introuvable." >&2
    exit 1
  fi
}

function sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | awk '{print $1}'
  else
    echo "Erreur : ni 'sha256sum' ni 'shasum' n'est disponible pour le calcul de hash." >&2
    return 1
  fi
}

function json_escape() {
  local value="$1"
  value=${value//\\/\\\\}
  value=${value//"/\\"}
  value=${value//$'\n'/\\n}
  echo "$value"
}

function parse_tsv_line() {
  local line="$1"
  IFS=$'\t' read -r sourceType library version url docId contentCss removeCss <<< "$line"

  sourceType=${sourceType:-$DEFAULT_SOURCE_TYPE}
  library=${library:-$DEFAULT_LIBRARY}
  version=${version:-$DEFAULT_VERSION}
  url=${url:-""}
  docId=${docId:-""}
  contentCss=${contentCss:-$SELECTOR_CONTENT_CSS}
  removeCss=${removeCss:-$SELECTOR_REMOVE_CSS}

  if [[ -z "$url" ]]; then
    echo "Ligne ignorée : URL manquante (sourceType=$sourceType, library=$library, version=$version)." >&2
    return 1
  fi

  typeset -gA CURRENT_LINE
  CURRENT_LINE=(
    sourceType "$sourceType"
    library "$library"
    version "$version"
    url "$url"
    docId "$docId"
    contentCss "$contentCss"
    removeCss "$removeCss"
  )
  return 0
}

function build_payload() {
  local contentCss="$1"
  local removeCssCsv="$2"

  local -a fields selectors remove_items
  local urlEsc sourceEsc libraryEsc versionEsc docEsc contentEsc

  urlEsc=$(json_escape "${CURRENT_LINE[url]}")
  sourceEsc=$(json_escape "${CURRENT_LINE[sourceType]}")
  libraryEsc=$(json_escape "${CURRENT_LINE[library]}")
  versionEsc=$(json_escape "${CURRENT_LINE[version]}")
  docEsc=$(json_escape "${CURRENT_LINE[docId]}")
  contentEsc=$(json_escape "$contentCss")

  if [[ -n "$contentCss" ]]; then
    selectors+=("\"contentCss\":\"$contentEsc\"")
  fi

  if [[ -n "$removeCssCsv" ]]; then
    local cssEntry
    for cssEntry in ${(s:,:)removeCssCsv}; do
      cssEntry=${cssEntry## }
      cssEntry=${cssEntry%% }
      if [[ -z "$cssEntry" ]]; then
        continue
      fi
      cssEntry=$(json_escape "$cssEntry")
      remove_items+=("\"$cssEntry\"")
    done
    if (( ${#remove_items[@]} > 0 )); then
      selectors+=("\"removeCss\":[${(j:,:)remove_items}]")
    fi
  fi

  fields+=("\"url\":\"$urlEsc\"")
  fields+=("\"sourceType\":\"$sourceEsc\"")
  fields+=("\"library\":\"$libraryEsc\"")
  fields+=("\"version\":\"$versionEsc\"")

  if [[ -n "${CURRENT_LINE[docId]}" ]]; then
    fields+=("\"docId\":\"$docEsc\"")
  fi

  if (( ${#selectors[@]} > 0 )); then
    fields+=("\"selectors\":{${(j:,:)selectors}}")
  fi

  fields+=("\"maxChars\":${MAX_CHARS}")

  echo "{${(j:,:)fields}}"
}

function extract_json_value() {
  local body="$1"
  local key="$2"
  local default="$3"

  if [[ "$HAS_JQ" == "true" ]]; then
    local value
    value=$(printf '%s' "$body" | jq -r --arg k "$key" '.[$k] // empty' 2>/dev/null || true)
    if [[ -n "$value" ]]; then
      echo "$value"
      return 0
    fi
  else
    local pattern
    pattern='"'"$key"'"[[:space:]]*:[[:space:]]*"\([^"]*\)"'
    local value
    value=$(printf '%s' "$body" | sed -n "s/.*${pattern}.*/\\1/p" | head -n 1)
    if [[ -n "$value" ]]; then
      echo "$value"
      return 0
    fi
  fi

  echo "$default"
}

function summarize_response() {
  local http_code="$1"
  local body="$2"
  local url="$3"

  local documentKey ingested skipped chunks documentHash
  documentKey=$(extract_json_value "$body" "documentKey" "")
  documentHash=$(extract_json_value "$body" "documentHash" "")
  ingested=$(extract_json_value "$body" "ingested" "")
  skipped=$(extract_json_value "$body" "skipped" "")
  chunks=$(extract_json_value "$body" "chunksCreated" "")

  echo "---"
  echo "URL           : $url"
  echo "HTTP          : $http_code"
  echo "documentKey   : ${documentKey:-<inconnu>}"
  echo "ingested/skipped : ${ingested:-?}/${skipped:-?}"
  echo "chunksCreated : ${chunks:-?}"
  echo "documentHash  : ${documentHash:-<n/a>}"
}

function post_ingest() {
  local payload="$1"
  local url="$2"

  local ts
  ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)

  if [[ "$DRY_RUN" == "true" ]]; then
    echo "---"
    echo "[DRY_RUN] Appel POST $RAG_BASE_URL/ingestFromHtml pour $url"
    echo "$payload"
    echo "$ts DRY_RUN url=$url" >> "$LOG_FILE"
    printf '{"timestamp":"%s","url":"%s","dryRun":true,"payload":%s}\n' "$ts" "$(json_escape "$url")" "$payload" >> "$RESULTS_FILE"
    return 0
  fi

  local -a headers
  headers=("-H" "Content-Type: application/json")
  if [[ -n "$RAG_API_KEY" ]]; then
    headers+=("-H" "Authorization: Bearer $RAG_API_KEY")
  fi

  local response_file
  response_file=$(mktemp)
  local http_code body

  if ! http_code=$(curl -sS -o "$response_file" -w "%{http_code}" -X POST "$RAG_BASE_URL/ingestFromHtml" "${headers[@]}" -d "$payload"); then
    echo "Erreur curl vers $RAG_BASE_URL/ingestFromHtml" >&2
    echo "$ts curl_error url=$url" >> "$LOG_FILE"
    rm -f "$response_file"
    return 1
  fi

  body=$(cat "$response_file")
  rm -f "$response_file"

  echo "$ts http=$http_code url=$url" >> "$LOG_FILE"
  local body_compact
  body_compact=$(printf '%s' "$body" | tr '\n' ' ')
  printf '{"timestamp":"%s","url":"%s","httpCode":%s,"response":%s}\n' "$ts" "$(json_escape "$url")" "$http_code" "${body_compact:-{}}" >> "$RESULTS_FILE"

  summarize_response "$http_code" "$body" "$url"

  if [[ "$http_code" =~ ^2 ]]; then
    return 0
  else
    echo "Réponse complète : $body" >&2
    return 1
  fi
}

function main() {
  require_cmd curl
  require_cmd sed
  require_cmd awk

  if [[ -z "$RAG_BASE_URL" ]]; then
    echo "Erreur : RAG_BASE_URL doit être défini (ex: http://localhost:8082)." >&2
    exit 1
  fi

  mkdir -p "${SOURCES_FILE:h}" logs

  if [[ ! -f "$SOURCES_FILE" ]]; then
    echo "Erreur : fichier de sources introuvable : $SOURCES_FILE" >&2
    exit 1
  fi

  local line line_no=0 errors=0 processed=0

  while IFS= read -r line || [[ -n "$line" ]]; do
    ((line_no++))
    if [[ -z "${line//[[:space:]]/}" ]]; then
      continue
    fi
    if [[ "$line" == \#* ]]; then
      continue
    fi

    if ! parse_tsv_line "$line"; then
      ((errors++))
      if [[ "$FAIL_FAST" == "true" ]]; then
        echo "Arrêt en mode fail-fast (ligne $line_no)." >&2
        exit 1
      fi
      continue
    fi

    local payload selectors_remove
    selectors_remove="${CURRENT_LINE[removeCss]}"
    payload=$(build_payload "${CURRENT_LINE[contentCss]}" "$selectors_remove")
    if post_ingest "$payload" "${CURRENT_LINE[url]}"; then
      ((processed++))
    else
      ((errors++))
      if [[ "$FAIL_FAST" == "true" ]]; then
        echo "Arrêt en mode fail-fast après échec ligne $line_no." >&2
        exit 1
      fi
    fi
  done < "$SOURCES_FILE"

  echo "---"
  echo "Documents traités : $processed"
  echo "Erreurs           : $errors"

  if (( errors > 0 )); then
    exit 1
  fi

  exit 0
}

main "$@"
