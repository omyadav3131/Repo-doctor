# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Copy the maven wrapper and pom.xml first to cache dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Ensure the wrapper is executable
RUN chmod +x mvnw

# Download dependencies (this layer will be cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy the source code
COPY src src

# Build the application, skipping tests to speed up the build
RUN ./mvnw package -DskipTests

# Stage 2: Create the production image
FROM eclipse-temurin:21-jre-jammy AS production
WORKDIR /app

# Create a non-root user for better security
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy the built JAR file from the build stage
COPY --from=builder /app/target/*.jar app.jar

# Define default port (can be overridden by PaaS like Koyeb/Render)
ENV PORT=8080
EXPOSE $PORT

# Entrypoint to run the jar, binding to the platform's PORT environment variable
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT}"]
