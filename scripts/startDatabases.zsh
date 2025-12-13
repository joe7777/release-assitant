#!/usr/bin/env zsh
# Démarre postgres et vector-db via docker compose.
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname "${(%):-%N}")" && pwd)
ROOT_DIR="${SCRIPT_DIR:h}"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker n'est pas installé ou pas dans le PATH." >&2
  exit 1
fi

docker compose -f "$COMPOSE_FILE" up -d postgres vector-db
