#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then echo "Run sh scripts/setup-env.sh and configure your MySQL connection." >&2; exit 1; fi
java_cmd=java
if [ -n "${JAVA_HOME:-}" ]; then java_cmd="$JAVA_HOME/bin/java"; fi
version=$("$java_cmd" -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.specification.version = //p')
if [ "$version" != "17" ]; then
  echo "Java 17 is required for the native build; detected ${version:-unknown}. Set JAVA_HOME to a JDK 17 installation." >&2
  echo "Or use sh scripts/start.sh: Docker supplies Java 17 and MySQL automatically." >&2
  exit 1
fi
set -a
. ./.env
set +a
cd backend
exec sh ./mvnw spring-boot:run -Dspring-boot.run.arguments=--debug=false
