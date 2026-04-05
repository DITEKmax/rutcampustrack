FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 9094

ENTRYPOINT ["java", "-jar", "app.jar"]
