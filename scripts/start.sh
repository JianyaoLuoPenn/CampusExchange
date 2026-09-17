#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
command -v docker >/dev/null 2>&1 || { echo "Install/start Docker Desktop, then retry. Native startup is documented in README.md." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 is required." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker is not running. Open Docker Desktop, wait until ready, then retry." >&2; exit 1; }
sh scripts/setup-env.sh
# Compose validates required values without printing secrets.
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 180
echo "CampusExchange is ready. Database, API and frontend/API proxy health checks passed."
echo "Open http://localhost:5173 (or your WEB_PORT). Status: docker compose ps"
echo "Logs: docker compose logs -f backend. Stop without deleting data: docker compose down"
