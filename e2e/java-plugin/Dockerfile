# Build context is the repository root:
#   docker build -f e2e/java-plugin/Dockerfile -t e2e-java-plugin .
# The plugin is built from source, so the image never needs a published artifact.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY sdk/@launchdarkly/observability-java sdk/@launchdarkly/observability-java
COPY e2e/java-plugin e2e/java-plugin
WORKDIR /src/e2e/java-plugin
RUN ../../sdk/@launchdarkly/observability-java/gradlew -p . installDist --no-daemon --console=plain \
    -PmainClass=com.launchdarkly.observability.example.ServerExample

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/e2e/java-plugin/build/install/java-plugin-example /app
ENV PORT=8080
ENV BACKEND_URI="http://localhost:8080/"
EXPOSE 8080
CMD ["/app/bin/java-plugin-example"]
