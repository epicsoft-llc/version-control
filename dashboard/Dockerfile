# ---- build ------------------------------------------------------------------
FROM node:24-alpine AS build

WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- serve ------------------------------------------------------------------
FROM nginx:alpine

# The base image lags behind Alpine's security fixes; the upgrade brings them into the release.
RUN apk --no-cache upgrade

ARG APP_VERSION="development"
ARG BUILD_DATE="development"

LABEL org.opencontainers.image.title="Version Control Dashboard" \
      org.opencontainers.image.description="Infrastructure version overview — web interface" \
      org.opencontainers.image.vendor="epicsoft LLC" \
      org.opencontainers.image.version="$APP_VERSION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.source="https://gitlab.com/epicsoft-networks/version-control"

COPY --from=build /build/dist/vc-dashboard/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
