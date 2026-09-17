#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then echo "Copy .env.example to .env and set database credentials and JWT_SECRET." >&2; exit 1; fi
set -a
. ./.env
set +a
cd backend
exec sh ./mvnw spring-boot:run
