# Build context is the repository root:
#   docker build -f e2e/java-plugin-spring-boot/Dockerfile -t e2e-java .
# The plugin is built from source, so the image never needs a published artifact.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY sdk/@launchdarkly/observability-java sdk/@launchdarkly/observability-java
COPY e2e/java-plugin-spring-boot e2e/java-plugin-spring-boot
WORKDIR /src/e2e/java-plugin-spring-boot
RUN ../../sdk/@launchdarkly/observability-java/gradlew -p . bootJar --no-daemon --console=plain \
    && cp build/libs/*.jar /src/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
ENV PORT=8080
# e2e/tests/src/app_runner.py reads BACKEND_URI from the container to find the
# port to publish and the URL to health-check, as the other e2e apps do.
# DevSkim: ignore DS162092
ENV BACKEND_URI="http://localhost:8080/"
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
