# Vue Telegram Mini App release image. The context is ./frontends so the
# workspace lockfile and mobile-core package are available to npm ci/build.
FROM node:24.14-alpine AS builder

WORKDIR /workspace

COPY package.json package-lock.json ./
COPY tsconfig.foundation.json postcss.config.mjs ./
COPY mobile-core/package.json mobile-core/package.json
COPY pwa-vue/package.json pwa-vue/package.json
COPY tma-vue/package.json tma-vue/package.json
RUN npm ci

COPY mobile-core mobile-core
COPY pwa/src/api/generated/academic.types.ts pwa/src/api/generated/academic.types.ts
COPY pwa/src/api/generated/schedule.types.ts pwa/src/api/generated/schedule.types.ts
COPY pwa-vue pwa-vue
COPY tma-vue tma-vue

ARG VITE_PUBLIC_BASE=/mini-app/
ARG VITE_MOBILE_FIXTURE_MODE=false
ENV VITE_PUBLIC_BASE=$VITE_PUBLIC_BASE
ENV VITE_MOBILE_FIXTURE_MODE=$VITE_MOBILE_FIXTURE_MODE

RUN npm run build --workspace @rct/tma-vue

FROM nginx:1.27-alpine

COPY --from=builder /workspace/tma-vue/dist /usr/share/nginx/html
COPY tma-vue/nginx.conf /etc/nginx/conf.d/default.conf
