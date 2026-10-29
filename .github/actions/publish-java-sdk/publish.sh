#!/bin/bash

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

# `check` runs the unit tests plus the guards on what gets published: no
# OkHttp/Okio/Kotlin on the runtime classpath, and opentelemetry-api at
# compile scope in the POM. Nothing is uploaded if any of them fail.
echo "Verifying the artifact"
./gradlew :lib:build "${GRADLE_ARGS[@]}" || {
  echo "Gradle build/verification failed" >&2
  exit 1
}

echo "Publishing to Sonatype"
if [ "${LD_RELEASE_IS_PRERELEASE}" == "true" ]; then
  echo "PRERELEASE"
  ./gradlew publishToSonatype "${GRADLE_ARGS[@]}" || {
    echo "Gradle publish/release failed" >&2
    exit 1
  }
else
  echo "RELEASE"
  ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository "${GRADLE_ARGS[@]}" || {
    echo "Gradle publish/release failed" >&2
    exit 1
  }
fi
