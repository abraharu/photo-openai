# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-26-alpine AS build
WORKDIR /app

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:26-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=10 -XX:+UseSerialGC -XX:MaxDirectMemorySize=64m -Djava.security.egd=file:/dev/urandom"

COPY --from=build /app/target/photo-openai-telegram-bot-0.0.1-SNAPSHOT.jar app.jar

USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
