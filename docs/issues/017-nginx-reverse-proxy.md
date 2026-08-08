# feat(deploy): add Nginx reverse proxy for dashboard

**Labels:** `enhancement` `tier-3-infra` `deployment` `devops`
**Estimated effort:** 1-2 days

## Problem

The Vue dashboard is built as a static SPA but served separately from the Spring Boot API. There is no reverse proxy to serve both from a single domain, handle SSL, or consolidate CORS concerns.

## Proposed Solution

Add Nginx to the docker-compose stack as a reverse proxy that serves the Vue dist and proxies API requests to the Spring Boot container.

## Nginx Configuration

### nginx.conf

```nginx
upstream api_backend {
    server api:8080;
}

server {
    listen 80;
    server_name _;

    # Vue SPA static files
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API proxy
    location /api/ {
        proxy_pass http://api_backend/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
        proxy_connect_timeout 10s;
    }

    # WebSocket proxy
    location /ws {
        proxy_pass http://api_backend/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
    }

    # Health check (pass through)
    location /api/health {
        proxy_pass http://api_backend/api/health;
        proxy_set_header Host $host;
    }

    # Swagger UI (pass through)
    location /swagger-ui.html {
        proxy_pass http://api_backend/swagger-ui.html;
        proxy_set_header Host $host;
    }

    # Gzip compression
    gzip on;
    gzip_types text/plain application/json application/javascript text/css;
    gzip_min_length 1000;
}
```

### Dockerfile.nginx

```dockerfile
FROM nginx:alpine

# Copy custom config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy Vue dist
COPY swing-trade-dashboard/dist /usr/share/nginx/html

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost/healthcheck || exit 1

# Healthcheck endpoint
RUN echo "healthy" > /usr/share/nginx/html/healthcheck

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

## Updated docker-compose.yml

```yaml
services:
  # ... existing services ...

  nginx:
    build:
      context: .
      dockerfile: Dockerfile.nginx
    container_name: swing-trade-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      api:
        condition: service_healthy
    volumes:
      - nginx_data:/var/cache/nginx
      - ./ssl:/etc/nginx/ssl:ro  # SSL certs
    networks:
      - swingtrade-network
    mem_limit: 128m
```

## SSL Support

### ssl-nginx.conf (production)

```nginx
server {
    listen 443 ssl http2;
    server_name api.swingtrade.com;

    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # ... same location blocks ...
}

server {
    listen 80;
    server_name api.swingtrade.com;
    return 301 https://$host$request_uri;
}
```

## Files to Create

- `nginx.conf` - Development Nginx config
- `ssl-nginx.conf` - Production SSL Nginx config
- `Dockerfile.nginx` - Nginx Docker image
- `docker-compose.prod.yml` - Production compose overlay (optional)
- `.env.example` - Add `NGINX_PORT`, `DOMAIN` variables

## Files to Modify

- `docker-compose.yml` - Add nginx service
- `swing-trade-dashboard/vite.config.ts` - Update proxy config for dev
- `README.md` - Update deployment instructions

## Acceptance Criteria

- [ ] `nginx.conf` created with SPA fallback and API proxy
- [ ] `Dockerfile.nginx` builds and serves Vue dist
- [ ] Nginx service added to docker-compose.yml
- [ ] `/` serves Vue SPA with router fallback
- [ ] `/api/*` proxied to Spring Boot API
- [ ] `/ws` proxied with WebSocket upgrade headers
- [ ] Gzip compression enabled
- [ ] Health check endpoint works
- [ ] `docker-compose up -d` brings up Nginx correctly
- [ ] Dashboard accessible on port 80
- [ ] SSL config template provided for production

## Notes

- For local development, Nginx is optional (Vite proxy suffices)
- SSL certs should be mounted as volumes, not baked into image
- Consider adding Cloudflare CDN in front of Nginx for production
- The `try_files $uri $uri/ /index.html` is critical for Vue hash history
