# syntax=docker/dockerfile:1
#
# SiteCheck backend image (NFR-09). Two stages: build with the JDK, run on a slim JRE.
# Tests are not run here - they need Docker themselves (Testcontainers) and run in CI instead.
#
#   docker build -t sitecheck-backend .
#   docker compose --profile app up -d      # runs it next to Postgres and LocalStack

# ---------------------------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

# Maven wrapper and pom first: this layer (all dependencies) is reused until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# A Windows checkout may have CRLF line endings and no exec bit; normalise both.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/SiteCheck-*.jar extract --destination extracted

# ---------------------------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-noble

# Don't run as root inside the container.
RUN useradd --system --uid 10001 --no-create-home sitecheck
WORKDIR /app

# Libraries and app code in separate layers: a code change only re-ships the small app jar.
COPY --from=build /workspace/extracted/lib/ lib/
COPY --from=build /workspace/extracted/SiteCheck-*.jar app.jar

USER sitecheck
EXPOSE 8080
# Configuration comes from environment variables: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET,
# S3_BUCKET, S3_ENDPOINT, S3_PRESIGN_ENDPOINT, AWS_REGION (see src/main/resources/application.yml).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
