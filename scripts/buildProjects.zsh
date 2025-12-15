#!/usr/bin/env zsh
# Build séquentiel des projets Java puis du frontend.
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname "${(%):-%N}")" && pwd)
ROOT_DIR="${SCRIPT_DIR:h}"

require_tools() {
  for bin in mvn npm; do
    if ! command -v "$bin" >/dev/null 2>&1; then
      echo "L'outil '$bin' est requis pour ce script." >&2
      exit 1
    fi
  done
}

build_java_project() {
  local project_dir="$1"
  echo "➡️  Build Maven : $project_dir"
  (cd "$ROOT_DIR/$project_dir" && mvn -DskipTests clean package)
}

build_frontend() {
  echo "➡️  Build frontend"
  cd "$ROOT_DIR/frontend"
  npm install
  npm run build
}

main() {
  require_tools

  build_java_project "backend"
  build_java_project "mcp-methodology"
  build_java_project "mcp-project-analyzer"
  build_java_project "mcp-knowledge-rag"
  build_java_project "mcp-server"
  build_java_project "llm-host"
  build_frontend

  echo "✅ Builds terminés"
}

main "$@"
