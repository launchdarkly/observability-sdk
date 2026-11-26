#!/bin/bash
# Verifies the plugin and its Compose library, then publishes both to Maven Central.

set -ue

GRADLE_ARGS=(
  --no-daemon
  --console=plain
  -Psigning.keyId="${SIGNING_KEY_ID}"
  -Psigning.password="${SIGNING_KEY_PASSPHRASE}"
  -Psigning.secretKeyRingFile="${SIGNING_SECRET_KEY_RING_FILE}"
  -PsonatypeUsername="${SONATYPE_USER_NAME}"
  -PsonatypePassword="${SONATYPE_PASSWORD}"
)

# check runs the tests and the coverage gate, and the guards on what is published: the plugin's
# POM depends only on OpenTelemetry and OkHttp 4, the Compose library's on nothing, both carry the
# metadata Central requires, and no OkHttp 5 reaches the runtime classpath. Nothing is uploaded if
# any of them fail. The desktop tests need a display.
echo "Verifying the artifacts"
xvfb-run --auto-servernum ./gradlew check javadoc "${GRADLE_ARGS[@]}"

if [ "${LD_RELEASE_DRY_RUN}" == "true" ]; then
  echo "Dry run: building the signed artifacts without publishing"
  ./gradlew signMavenJavaPublication "${GRADLE_ARGS[@]}"
  exit 0
fi

if [ "${LD_RELEASE_IS_PRERELEASE}" == "true" ]; then
  echo "Publishing a prerelease"
  ./gradlew publishToSonatype "${GRADLE_ARGS[@]}"
else
  echo "Publishing a release"
  ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository "${GRADLE_ARGS[@]}"
fi
