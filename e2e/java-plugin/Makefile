.PHONY: run check consumer-check

SDK_DIR := ../../sdk/@launchdarkly/observability-java
SDK_GRADLE := $(SDK_DIR)/gradlew
GRADLE_ARGS := --no-daemon --console=plain

# Self-checking example against the plugin sources in this repository.
run check:
	$(SDK_GRADLE) -p $(CURDIR) run $(GRADLE_ARGS)

# Publishes the plugin to ~/.m2 and builds this example against that
# artifact, the way an application would. Catches POM scope mistakes and
# transitive dependency leaks that the composite build hides.
consumer-check:
	cd $(SDK_DIR) && ./gradlew :lib:publishToMavenLocal $(GRADLE_ARGS)
	$(SDK_GRADLE) -p $(CURDIR) check run $(GRADLE_ARGS) -PuseMavenLocal
