#!/usr/bin/env zsh
# Arrête tous les services du docker-compose sauf les bases de données (postgres, vector-db).
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname "${(%):-%N}")" && pwd)
ROOT_DIR="${SCRIPT_DIR:h}"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker n'est pas installé ou pas dans le PATH." >&2
  exit 1
fi

stop_non_db_services() {
  local services
  services=($(docker compose -f "$COMPOSE_FILE" ps --services))

  local to_stop=()
  for svc in "${services[@]}"; do
    if [[ "$svc" != "postgres" && "$svc" != "vector-db" ]]; then
      to_stop+=("$svc")
    fi
  done

  if (( ${#to_stop[@]} == 0 )); then
    echo "Aucun service à arrêter en dehors de postgres et vector-db."
    return 0
  fi

  echo "Arrêt des services : ${to_stop[*]}"
  docker compose -f "$COMPOSE_FILE" stop ${to_stop[@]}
}

stop_non_db_services
