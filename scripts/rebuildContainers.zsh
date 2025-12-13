#!/usr/bin/env zsh
# Supprime les conteneurs (hors postgres/vector-db) puis rebuild toutes les images docker du projet.
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname "${(%):-%N}")" && pwd)
ROOT_DIR="${SCRIPT_DIR:h}"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker n'est pas installé ou pas dans le PATH." >&2
  exit 1
fi

remove_non_db_containers() {
  local services
  services=($(docker compose -f "$COMPOSE_FILE" ps --services))

  local targets=()
  for svc in "${services[@]}"; do
    if [[ "$svc" != "postgres" && "$svc" != "vector-db" ]]; then
      targets+=("$svc")
    fi
  done

  if (( ${#targets[@]} == 0 )); then
    echo "Aucun conteneur à supprimer."
    return 0
  fi

  echo "Suppression des conteneurs : ${targets[*]}"
  docker compose -f "$COMPOSE_FILE" rm -fs ${targets[@]}
}

rebuild_images() {
  echo "Reconstruction des images Docker du projet"
  docker compose -f "$COMPOSE_FILE" build
}

remove_non_db_containers
rebuild_images
