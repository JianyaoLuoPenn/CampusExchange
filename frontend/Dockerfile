FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=/api/campus
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

FROM node:22-alpine
WORKDIR /app
# Reuse Vite's preview server for the local demo, with the same API proxy as development.
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY --from=build /app/package.json /app/vite.config.ts ./
RUN mkdir -p /app/node_modules/.vite-temp && chown -R node:node /app/node_modules/.vite-temp
USER node
EXPOSE 5173
CMD ["npm", "run", "preview", "--", "--host", "0.0.0.0", "--port", "5173", "--strictPort"]
