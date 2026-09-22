# Vue PWA release image. The context is ./frontends so the workspace lockfile
# and mobile-core package are available to npm ci and the build.
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

ARG VITE_PUBLIC_BASE=/app/
ARG VITE_APP_VERSION=0.0.0
ARG VITE_MIN_SUPPORTED_VERSION=
ARG VITE_FORCE_UPDATE=true
ARG VITE_MOBILE_FIXTURE_MODE=false
ENV VITE_PUBLIC_BASE=$VITE_PUBLIC_BASE
ENV VITE_APP_VERSION=$VITE_APP_VERSION
ENV VITE_MIN_SUPPORTED_VERSION=$VITE_MIN_SUPPORTED_VERSION
ENV VITE_FORCE_UPDATE=$VITE_FORCE_UPDATE
ENV VITE_MOBILE_FIXTURE_MODE=$VITE_MOBILE_FIXTURE_MODE

RUN npm run build --workspace @rct/pwa-vue

FROM nginx:1.27-alpine

COPY --from=builder /workspace/pwa-vue/dist /usr/share/nginx/html
COPY pwa-vue/nginx.conf /etc/nginx/conf.d/default.conf
