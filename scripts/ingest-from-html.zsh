#!/usr/bin/env zsh
# Script d'ingestion hors-ligne des pages HTML dans mcp-knowledge-rag via l'API /ingestFromHtml
# ou via le tool MCP rag.ingestFromHtml exposé par mcp-server.
# Utilisation :
#   DRY_RUN=true ./scripts/ingest-from-html.zsh
#   USE_MCP_TOOL=true MCP_BASE_URL="http://localhost:8085" ./scripts/ingest-from-html.zsh
#   RAG_BASE_URL="http://localhost:8082" RAG_API_KEY="..." ./scripts/ingest-from-html.zsh
set -euo pipefail

RAG_BASE_URL=${RAG_BASE_URL:-"http://localhost:8082"}
MCP_BASE_URL=${MCP_BASE_URL:-"http://localhost:8085"}
USE_MCP_TOOL=${USE_MCP_TOOL:-"false"}
RAG_API_KEY=${RAG_API_KEY:-""}
DRY_RUN=${DRY_RUN:-"false"}
MAX_CHARS=${MAX_CHARS:-"200000"}
DEFAULT_SOURCE_TYPE=${DEFAULT_SOURCE_TYPE:-"SPRING_RELEASE_NOTE"}
DEFAULT_LIBRARY=${DEFAULT_LIBRARY:-"spring-boot"}
DEFAULT_VERSION=${DEFAULT_VERSION:-"latest"}
SELECTOR_CONTENT_CSS=${SELECTOR_CONTENT_CSS:-""}
SELECTOR_REMOVE_CSS=${SELECTOR_REMOVE_CSS:-""}
# Fichier CSV (séparateur ;) avec les colonnes : sourceType;library;version;url;docId;contentCss;removeCss
SOURCES_FILE=${SOURCES_FILE:-"rag_sources/sources.csv"}
LOG_FILE=${LOG_FILE:-"logs/ingest-from-html.log"}
RESULTS_FILE=${RESULTS_FILE:-"logs/ingest-from-html-results.jsonl"}
HTTP_LOG_FILE=${HTTP_LOG_FILE:-"logs/ingest-from-html-http.log"}
FAIL_FAST=${FAIL_FAST:-"false"}

typeset -gA CURRENT_LINE
HAS_JQ="false"
if command -v jq >/dev/null 2>&1; then
  HAS_JQ="true"
fi

function timestamp_utc() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

function log_line() {
  local level="$1"
  shift
  local ts msg
  ts=$(timestamp_utc)
  msg="$ts [$level] $*"
  echo "$msg" | tee -a "$LOG_FILE"
}

function log_http() {
  local direction="$1"
  local ts
  ts=$(timestamp_utc)
  shift
  echo "$ts [$direction] $*" >> "$HTTP_LOG_FILE"
}

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

function parse_csv_line() {
  local line="$1"
  IFS=';' read -r sourceType library version url docId contentCss removeCss <<< "$line"

  sourceType=${sourceType%$'\r'}
  library=${library%$'\r'}
  version=${version%$'\r'}
  url=${url%$'\r'}
  docId=${docId%$'\r'}
  contentCss=${contentCss%$'\r'}
  removeCss=${removeCss%$'\r'}

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

  if [[ "$USE_MCP_TOOL" == "true" ]]; then
    if [[ -n "$contentCss" ]]; then
      selectors+=("\"$contentEsc\"")
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
        selectors+=("\"$cssEntry\"")
      done
    fi
  else
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
  fi

  fields+=("\"url\":\"$urlEsc\"")
  fields+=("\"sourceType\":\"$sourceEsc\"")
  fields+=("\"library\":\"$libraryEsc\"")
  fields+=("\"version\":\"$versionEsc\"")

  if [[ -n "${CURRENT_LINE[docId]}" ]]; then
    fields+=("\"docId\":\"$docEsc\"")
  fi

  if (( ${#selectors[@]} > 0 )); then
    if [[ "$USE_MCP_TOOL" == "true" ]]; then
      fields+=("\"selectors\":[${(j:,:)selectors}]")
    else
      fields+=("\"selectors\":{${(j:,:)selectors}}")
    fi
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

  local chunksStored chunksSkipped documentHash
  documentHash=$(extract_json_value "$body" "documentHash" "")
  chunksStored=$(extract_json_value "$body" "chunksStored" "")
  chunksSkipped=$(extract_json_value "$body" "chunksSkipped" "")

  echo "---"
  echo "URL           : $url"
  echo "HTTP          : $http_code"
  echo "chunksStored  : ${chunksStored:-?}"
  echo "chunksSkipped : ${chunksSkipped:-?}"
  echo "documentHash  : ${documentHash:-<n/a>}"
}

function post_ingest() {
  local payload="$1"
  local url="$2"

  local ts
  ts=$(timestamp_utc)

  local target_base target_path
  if [[ "$USE_MCP_TOOL" == "true" ]]; then
    target_base="$MCP_BASE_URL/api/rag"
    target_path="/ingest/html"
  else
    target_base="$RAG_BASE_URL"
    target_path="/ingestFromHtml"
  fi

  if [[ "$DRY_RUN" == "true" ]]; then
    log_line INFO "[DRY_RUN] Appel POST $target_base$target_path pour $url"
    log_http REQUEST "POST $target_base$target_path" "payload=$payload"
    printf '{"timestamp":"%s","url":"%s","dryRun":true,"payload":%s}\n' "$ts" "$(json_escape "$url")" "$payload" >> "$RESULTS_FILE"
    return 0
  fi

  local -a headers
  headers=("-H" "Content-Type: application/json")
  if [[ "$USE_MCP_TOOL" != "true" && -n "$RAG_API_KEY" ]]; then
    headers+=("-H" "Authorization: Bearer $RAG_API_KEY")
  fi

  local safe_headers
  safe_headers="Content-Type: application/json"
  if [[ "$USE_MCP_TOOL" != "true" && -n "$RAG_API_KEY" ]]; then
    safe_headers+="; Authorization: Bearer ****"
  fi

  log_line INFO "Envoi requête vers $target_base$target_path (url=$url)"
  log_line DEBUG "Corps de requête: $payload"
  log_http REQUEST "POST $target_base$target_path" "headers=$safe_headers" "payload=$payload"

  local response_file
  response_file=$(mktemp)
  local http_code body

  if ! http_code=$(curl -sS -o "$response_file" -w "%{http_code}" -X POST "$target_base$target_path" "${headers[@]}" -d "$payload"); then
    echo "Erreur curl vers $target_base$target_path" >&2
    log_line ERROR "Erreur curl vers $target_base$target_path pour url=$url"
    rm -f "$response_file"
    return 1
  fi

  body=$(cat "$response_file")
  rm -f "$response_file"

  log_line INFO "Réponse HTTP $http_code pour url=$url"
  local body_compact
  body_compact=$(printf '%s' "$body" | tr '\n' ' ')
  log_line DEBUG "Corps de réponse: $body_compact"
  log_http RESPONSE "code=$http_code" "url=$url" "body=$body_compact"
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

  if [[ "$USE_MCP_TOOL" == "true" ]]; then
    if [[ -z "$MCP_BASE_URL" ]]; then
      echo "Erreur : MCP_BASE_URL doit être défini (ex: http://localhost:8085)." >&2
      exit 1
    fi
  else
    if [[ -z "$RAG_BASE_URL" ]]; then
      echo "Erreur : RAG_BASE_URL doit être défini (ex: http://localhost:8082)." >&2
      exit 1
    fi
  fi

  mkdir -p "${SOURCES_FILE:h}" "${LOG_FILE:h}" "${RESULTS_FILE:h}" "${HTTP_LOG_FILE:h}"
  : > "$LOG_FILE"
  : > "$HTTP_LOG_FILE"
  : > "$RESULTS_FILE"
  log_line INFO "=== Démarrage ingestion depuis HTML ==="
  if [[ "$USE_MCP_TOOL" == "true" ]]; then
    log_line INFO "Configuration: USE_MCP_TOOL=$USE_MCP_TOOL, MCP_BASE_URL=$MCP_BASE_URL, DRY_RUN=$DRY_RUN, MAX_CHARS=$MAX_CHARS, DEFAULT_SOURCE_TYPE=$DEFAULT_SOURCE_TYPE"
  else
    log_line INFO "Configuration: RAG_BASE_URL=$RAG_BASE_URL, DRY_RUN=$DRY_RUN, MAX_CHARS=$MAX_CHARS, DEFAULT_SOURCE_TYPE=$DEFAULT_SOURCE_TYPE"
  fi
  log_line INFO "Fichiers: sources=$SOURCES_FILE, log=$LOG_FILE, http_log=$HTTP_LOG_FILE, résultats=$RESULTS_FILE"

  if [[ ! -f "$SOURCES_FILE" ]]; then
    echo "Erreur : fichier de sources introuvable : $SOURCES_FILE" >&2
    exit 1
  fi

  local line line_no=0 errors=0 processed=0

  while IFS= read -r line || [[ -n "$line" ]]; do
    ((++line_no))
    if [[ -z "${line//[[:space:]]/}" ]]; then
      continue
    fi
    if [[ "$line" == \#* ]]; then
      continue
    fi

    if ! parse_csv_line "$line"; then
      ((++errors))
      log_line ERROR "Ligne $line_no ignorée (CSV invalide ou URL manquante)"
      if [[ "$FAIL_FAST" == "true" ]]; then
        echo "Arrêt en mode fail-fast (ligne $line_no)." >&2
        exit 1
      fi
      continue
    fi

    local payload selectors_remove
    selectors_remove="${CURRENT_LINE[removeCss]}"
    payload=$(build_payload "${CURRENT_LINE[contentCss]}" "$selectors_remove")
    log_line INFO "Ligne $line_no : url=${CURRENT_LINE[url]} sourceType=${CURRENT_LINE[sourceType]} library=${CURRENT_LINE[library]} version=${CURRENT_LINE[version]}"
    if [[ -n "$selectors_remove" ]]; then
      log_line DEBUG "Sélecteurs de suppression: $selectors_remove"
    fi
    if post_ingest "$payload" "${CURRENT_LINE[url]}"; then
      ((++processed))
    else
      ((++errors))
      if [[ "$FAIL_FAST" == "true" ]]; then
        echo "Arrêt en mode fail-fast après échec ligne $line_no." >&2
        exit 1
      fi
    fi
  done < "$SOURCES_FILE"

  echo "---"
  echo "Documents traités : $processed"
  echo "Erreurs           : $errors"

  log_line INFO "Traitement terminé : $processed succès, $errors erreurs"

  if (( errors > 0 )); then
    exit 1
  fi

  exit 0
}

main "$@"
