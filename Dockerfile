# ==============================================================================
# Stage 1: Build Java 17 + Spring Boot 3 Application
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy source and static WebGL assets
COPY src ./src

# Package production executable jar
RUN mvn -B clean package -DskipTests

# ==============================================================================
# Stage 2: Minimalist Production JRE Runtime
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Project SAARTHI Core Engineering"
LABEL description="Smart Autonomous Assistant for Resilient Tech-Driven Horticulture & Indoor Farming"

WORKDIR /app

# Create non-root user and persistent data folder
RUN addgroup -S saarthi && adduser -S saarthi -G saarthi && \
    mkdir -p /app/data && chown -R saarthi:saarthi /app

USER saarthi:saarthi

# Copy artifact from builder stage
COPY --from=builder /build/target/saarthi-backend-*.jar /app/saarthi-backend.jar

# Expose WebGL HUD and REST API port
EXPOSE 8080

# Environment Defaults
ENV SERVER_PORT=8080
ENV SPRING_DATASOURCE_URL="jdbc:h2:file:/app/data/saarthidb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/saarthi-backend.jar"]
