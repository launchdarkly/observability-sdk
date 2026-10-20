.PHONY: build run test javaagent-smoke consumer-check

SDK_DIR := ../../sdk/@launchdarkly/observability-java
SDK_GRADLE := $(SDK_DIR)/gradlew
GRADLE_ARGS := --no-daemon --console=plain

# Builds the executable Spring Boot jar at build/libs/*.jar.
build:
	$(SDK_GRADLE) -p $(CURDIR) bootJar $(GRADLE_ARGS)

# Runs the web backend on PORT (default 8080). Honours LAUNCHDARKLY_SDK_KEY,
# OTEL_EXPORTER_OTLP_ENDPOINT, LD_OBSERVABILITY_BACKEND_URL and HTTPS_PROXY.
run:
	$(SDK_GRADLE) -p $(CURDIR) bootRun $(GRADLE_ARGS)

# Unit tests plus the runtime-classpath guard.
test:
	$(SDK_GRADLE) -p $(CURDIR) check $(GRADLE_ARGS)

# Runs the jar under the OpenTelemetry Java agent, which owns
# GlobalOpenTelemetry, and checks that flag evaluations still reach the
# plugin's exporter (a local collector).
javaagent-smoke:
	$(SDK_GRADLE) -p $(CURDIR) bootJar javaagentJar $(GRADLE_ARGS)
	python3 scripts/javaagent_smoke.py build/libs/java-plugin-spring-boot-example.jar build/agent/opentelemetry-javaagent.jar

# Publishes the plugin to ~/.m2 and builds the app against that artifact.
consumer-check:
	cd $(SDK_DIR) && ./gradlew :lib:publishToMavenLocal $(GRADLE_ARGS)
	$(SDK_GRADLE) -p $(CURDIR) check bootJar $(GRADLE_ARGS) -PuseMavenLocal
