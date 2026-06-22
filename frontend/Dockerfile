# Production frontend image for BudgetSpace AI: build the static Vite bundle, serve it with nginx.
# This replaces the dev Vite server (docker-compose.override.yml) which is NOT for production.
# Build:  docker build -t budgetspace-frontend --build-arg VITE_API_BASE_URL=https://api.yourdomain ./frontend
#
# The SPA calls the backend at the build-time VITE_API_BASE_URL (see src/api/client.ts); the backend's
# CORS_ALLOWED_ORIGINS must allow-list this frontend's origin.

# --- Build stage -----------------------------------------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# --- Run stage -------------------------------------------------------------------------------------------------
FROM nginx:1.27-alpine AS run
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
