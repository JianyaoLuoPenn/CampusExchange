#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -f .env ]; then
  echo ".env already exists; left unchanged. Required for Docker: DB_PASSWORD, MYSQL_ROOT_PASSWORD, JWT_SECRET."
  exit 0
fi
command -v openssl >/dev/null 2>&1 || { echo "OpenSSL is needed to generate local credentials." >&2; exit 1; }
umask 077
cp .env.example .env
# Values are generated locally, never printed and never uploaded.
secret=$(openssl rand -hex 32)
db_password=$(openssl rand -hex 24)
root_password=$(openssl rand -hex 24)
sed -e "s/replace-with-a-random-secret-at-least-32-bytes-long/$secret/" \
    -e "s/replace-with-local-database-password/$db_password/" \
    -e "s/replace-with-local-root-password/$root_password/" .env > .env.setup-tmp
mv .env.setup-tmp .env
echo "Created .env with random local credentials. Default payment mode is mock."
