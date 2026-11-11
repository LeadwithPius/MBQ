# Build MBQ with Java 21 and Maven
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy Maven project files and source
COPY pom.xml .
COPY src ./src

# Build the Spring Boot executable JAR.
# Tests are run separately before production release.
RUN mvn -B -DskipTests package

# Run the application on a small Java 21 runtime image
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 --create-home appuser

COPY --from=build /workspace/target/MBQ-0.0.1-SNAPSHOT.jar /app/app.jar

USER appuser

EXPOSE 8081

# Render supplies PORT at runtime. Fall back to 8081 for local Docker use.
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8081} -jar /app/app.jar"]
