# Pact relay — a tiny stateless Node service. Build from the repo root:
#   docker build -f server/Dockerfile -t pact-relay .
# The engine is a local workspace dependency, so the build context is the repo.

FROM node:22-alpine AS build
WORKDIR /app
# Workspace manifests first for layer caching.
COPY package.json package-lock.json ./
COPY packages/engine/package.json packages/engine/
COPY server/package.json server/
RUN npm ci
# Sources, then build engine + relay to dist.
COPY packages/engine packages/engine
COPY server server
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
# Bring the built workspaces and production deps only.
COPY --from=build /app/package.json /app/package-lock.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/packages/engine/package.json ./packages/engine/package.json
COPY --from=build /app/packages/engine/dist ./packages/engine/dist
COPY --from=build /app/server/package.json ./server/package.json
COPY --from=build /app/server/dist ./server/dist
EXPOSE 8787
CMD ["node", "server/dist/index.js"]
