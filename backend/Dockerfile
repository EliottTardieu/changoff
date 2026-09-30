FROM docker.io/library/node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM docker.io/library/maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B -ntp dependency:resolve
COPY backend/src ./src
COPY --from=frontend /frontend/dist/changoff/browser/ ./src/main/resources/static/
RUN mvn -B -ntp verify
FROM docker.io/library/eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /build/target/changoff-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
