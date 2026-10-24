# Build from ./frontends: use the canonical workspace lockfile and shared sources.
FROM node:24.14-alpine AS builder

WORKDIR /workspace

COPY package.json package-lock.json ./
COPY tsconfig.foundation.json postcss.config.mjs dev-server.ts ./
COPY mobile-core/package.json mobile-core/package.json
COPY pwa-vue/package.json pwa-vue/package.json
COPY tma-vue/package.json tma-vue/package.json
COPY web-vue/package.json web-vue/package.json
RUN --mount=type=secret,id=proxy_ca,required=false \
    if [ -f /run/secrets/proxy_ca ]; then \
        NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca npm ci --strict-ssl=true; \
    else \
        npm ci --strict-ssl=true; \
    fi

COPY mobile-core mobile-core
COPY pwa-vue pwa-vue
COPY tma-vue tma-vue
COPY shared shared
COPY web-vue web-vue

# WEB remains the root SPA; only its own dist is shipped, not PWA/TMA public files.
RUN npm run build --workspace @rct/web-vue

FROM nginx:1.27-alpine

COPY --from=builder /workspace/web-vue/dist /usr/share/nginx/html
RUN chmod -R a+rX /usr/share/nginx/html
COPY web-vue/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
