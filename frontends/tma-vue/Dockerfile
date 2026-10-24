# Vue Telegram Mini App release image. The context is ./frontends so the
# workspace lockfile and mobile-core package are available to npm ci/build.
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

ARG VITE_PUBLIC_BASE=/mini-app/
ARG VITE_MOBILE_FIXTURE_MODE=false
ENV VITE_PUBLIC_BASE=$VITE_PUBLIC_BASE
ENV VITE_MOBILE_FIXTURE_MODE=$VITE_MOBILE_FIXTURE_MODE

RUN npm run build --workspace @rct/tma-vue

FROM nginx:1.27-alpine

COPY --from=builder /workspace/tma-vue/dist /usr/share/nginx/html
COPY tma-vue/nginx.conf /etc/nginx/conf.d/default.conf
