#!/usr/bin/env zsh
# Démarre tous les services applicatifs sauf postgres et vector-db.
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname "${(%):-%N}")" && pwd)
ROOT_DIR="${SCRIPT_DIR:h}"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker n'est pas installé ou pas dans le PATH." >&2
  exit 1
fi

docker compose -f "$COMPOSE_FILE" up -d backend frontend mcp-project-analyzer mcp-knowledge-rag mcp-methodology
