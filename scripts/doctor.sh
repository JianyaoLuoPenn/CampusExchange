#!/usr/bin/env sh
set -u
cd "$(dirname "$0")/.."
printf 'Workspace: %s\n' "$PWD"
if command -v docker >/dev/null 2>&1; then
  docker compose version 2>/dev/null || true
  if docker info >/dev/null 2>&1; then echo "Docker engine: ready"; else echo "Docker engine: not running (open Docker Desktop)"; fi
else echo "Docker: not installed"; fi
if [ -f .env ]; then echo ".env: present (contents hidden)"; else echo ".env: missing; run sh scripts/setup-env.sh"; fi
java_cmd=java
if [ -n "${JAVA_HOME:-}" ]; then java_cmd="$JAVA_HOME/bin/java"; fi
if command -v "$java_cmd" >/dev/null 2>&1; then "$java_cmd" -version 2>&1 | head -n 1; else echo "Native Java: missing (Docker includes Java 17)"; fi
if command -v node >/dev/null 2>&1; then node --version; else echo "Native Node: missing (Docker includes Node 22)"; fi
if command -v curl >/dev/null 2>&1; then
  curl -s --max-time 3 -o /dev/null -w 'Frontend HTTP: %{http_code}\n' http://localhost:5173 || true
  curl -s --max-time 3 -o /dev/null -w 'Backend HTTP: %{http_code}\n' http://localhost:8080/api/campus/config || true
fi
echo "Active entrypoint: backend/src/main/java/com/zosh/EcommerceMultiVendorApplication.java"
