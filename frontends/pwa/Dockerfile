# Stage 1: Build
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ARG VITE_APP_VERSION=0.0.0
ARG VITE_MIN_SUPPORTED_VERSION=
ARG VITE_FORCE_UPDATE=true
ENV VITE_APP_VERSION=$VITE_APP_VERSION
ENV VITE_MIN_SUPPORTED_VERSION=$VITE_MIN_SUPPORTED_VERSION
ENV VITE_FORCE_UPDATE=$VITE_FORCE_UPDATE
RUN node scripts/write-version-policy.mjs
RUN npm run build

# Stage 2: Runtime
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
