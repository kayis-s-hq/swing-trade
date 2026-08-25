# ==============================================================================
# SwingTrade Dashboard - Nginx Static Serve
# ==============================================================================
# dist/ is built OUTSIDE Docker, on the Mac, from a git worktree checked out
# to the `stage` branch (see dev-stack.sh `stage`). Nginx just serves the
# static files and proxies /api to the backend.
# ==============================================================================

FROM nginx:alpine

COPY dist/ /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
