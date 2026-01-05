# Build stage
FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /workspace/app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=default \
    QDRANT_HOST=vector-db \
    QDRANT_PORT=6333 \
    MCP_WORKSPACE_ROOT=/workspaces

COPY --from=builder /workspace/app/target/*.jar /app/app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
