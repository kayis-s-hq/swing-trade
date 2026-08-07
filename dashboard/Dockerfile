# ==============================================================================
# SwingTrade Dashboard - Nginx Static Serve
# ==============================================================================
# Builds frontend locally, copies dist/ to nginx image.
# Nginx serves static files and proxies /api to the backend.
# ==============================================================================

# Stage 1: Build
FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install 2>/dev/null || npm ci 2>/dev/null

COPY . .
RUN npm run build

# Stage 2: Serve
FROM nginx:alpine

# Custom nginx config
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]