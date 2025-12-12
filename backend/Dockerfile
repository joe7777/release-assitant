# Build stage
FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /workspace/backend

# Copy Maven descriptors first to leverage dependency caching
COPY pom.xml .
COPY backend-api/pom.xml backend-api/
COPY backend-core/pom.xml backend-core/
COPY backend-infra/pom.xml backend-infra/

RUN mvn -B -pl backend-api -am dependency:go-offline

# Copy sources and build the backend-api jar
COPY . .
RUN mvn -B -pl backend-api -am package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/backend/backend-api/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
