FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget && \
    addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY build/libs/*.jar app.jar

RUN chown app:app app.jar

USER app

EXPOSE 9094

ENTRYPOINT ["java", "-jar", "app.jar"]
