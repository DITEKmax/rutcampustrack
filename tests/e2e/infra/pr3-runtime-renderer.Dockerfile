# syntax=docker/dockerfile:1
# Native tools only; bind an explicit immutable, source-matched host-built JAR.
# Reuses the prepared Java21 image and never rebuilds the whole Gradle stack.
FROM eclipse-temurin:21-jdk-jammy

RUN --mount=type=secret,id=proxy_ca,required=true \
    apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca update \
    && DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca install -y --no-install-recommends \
        libreoffice-writer libreoffice-java-common poppler-utils fontconfig \
        fonts-dejavu fonts-liberation wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 1000 rct \
    && useradd --uid 1000 --gid rct --home-dir /app --create-home rct

WORKDIR /app
USER 1000:1000
EXPOSE 9095 19095
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
