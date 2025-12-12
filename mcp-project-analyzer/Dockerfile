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
    OPENAI_API_KEY= \
    QDRANT_URL= \
    VECTOR_DB_URL=

COPY --from=builder /workspace/app/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
