# Build from ./frontends using the workspace lockfile and the shared PWA demo sources.
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
COPY shared shared
COPY web-vue web-vue
COPY landing landing
ARG LANDING_SITEMAP_LASTMOD
RUN npm run build:landing
RUN find landing/dist-vue -type f \( -name '*.html' -o -name '*.css' -o -name '*.js' \
        -o -name '*.mjs' -o -name '*.json' -o -name '*.svg' -o -name '*.md' \
        -o -name '*.txt' -o -name '*.xml' \) -exec sh -ec '\
        for file do \
            gzip -9c < "$file" > "$file.gz"; \
            touch -r "$file" "$file.gz"; \
        done' sh {} +

FROM nginx:1.30.6-alpine3.24@sha256:0e52bd054126cdc040b1276c0af347aeb02a41b9cfe305d6094767ac30e97949
RUN apk upgrade --no-cache brotli-libs curl libcurl
COPY --from=builder /workspace/landing/dist-vue /usr/share/nginx/html
RUN chmod -R a+rX /usr/share/nginx/html
COPY landing/nginx.conf /etc/nginx/conf.d/default.conf
RUN nginx -t
EXPOSE 80
