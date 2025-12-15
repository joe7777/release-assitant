#!/usr/bin/env zsh
# Démarre le service Ollama et récupère les modèles nécessaires s'ils ne sont pas déjà présents.
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname "${(%):-%N}")" && pwd)
ROOT_DIR="${SCRIPT_DIR:h}"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker n'est pas installé ou pas dans le PATH." >&2
  exit 1
fi

start_ollama() {
  echo "Démarrage du service Ollama..."
  docker compose -f "$COMPOSE_FILE" up -d ollama
}

model_present() {
  local model="$1"
  docker compose -f "$COMPOSE_FILE" exec -T ollama ollama list --quiet | grep -Fx "$model" >/dev/null 2>&1
}

ensure_model() {
  local model="$1"
  if model_present "$model"; then
    echo "Le modèle '$model' est déjà présent."
  else
    echo "Téléchargement du modèle '$model'..."
    docker compose -f "$COMPOSE_FILE" exec -T ollama ollama pull "$model"
  fi
}

start_ollama

ensure_model "llama3.1:8b"
ensure_model "nomic-embed-text"
