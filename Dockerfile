# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create data directory for SQLite
RUN mkdir -p /app/data

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Environment variables (overridable)
ENV SQLITE_PATH=/app/data/jobs.db
ENV EMAIL_USER=""
ENV EMAIL_PASSWORD=""
ENV EMAIL_TO=""
ENV DRY_RUN=false

# Expose Actuator port for health checks and metrics
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
