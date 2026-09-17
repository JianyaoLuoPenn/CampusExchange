# CampusExchange frontend

See the [repository startup guide](../README.md) for the working backend, database and full Docker startup. This is not the original Vite template.

Native development: `npm ci && npm run dev`. The Vite dev/preview server forwards `/api` to `http://localhost:8080` (or `API_PROXY_TARGET`). Browser requests are same-origin; the project works whether opened as localhost or 127.0.0.1. Run `npm run lint`, `npm run build`, and `npm run test:e2e` as described in [TESTING.md](../docs/TESTING.md).

Original commercial pages remain as reference code and are not active routes. Campus UI entrypoint: `src/App.tsx`.
