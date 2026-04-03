#!/bin/bash -ex

# Publishes @launchdarkly/* npm packages using the npm CLI.
# Uses npm's native OIDC support for authentication (requires npm >= 11.5.1).

if $LD_RELEASE_IS_DRYRUN ; then
  echo "Doing a dry run of publishing."
  exit 0
fi

# Get the list of publishable @launchdarkly workspaces from yarn,
# excluding the root workspace and internal-only packages.
WORKSPACES=$(yarn workspaces list --json | \
  node -e "
    const lines = require('fs').readFileSync('/dev/stdin','utf8').trim().split('\n');
    const exclude = new Set(['@launchdarkly/observability-sdk', '@launchdarkly/observability-shared']);
    for (const line of lines) {
      const {name, location} = JSON.parse(line);
      if (name.startsWith('@launchdarkly/') && !exclude.has(name) && !location.includes('/example')) {
        console.log(location);
      }
    }
  ")

TAG_ARGS=""
if $LD_RELEASE_IS_PRERELEASE ; then
  echo "Publishing with prerelease tag."
  TAG_ARGS="--tag prerelease"
fi

for workspace in $WORKSPACES; do
  echo "Publishing $workspace..."

  # Use `yarn pack` to create a tarball with workspace:* references resolved
  # to real version numbers. Plain `npm publish <dir>` does not understand
  # Yarn's workspace: protocol, so consumers would see unresolvable deps.
  TARBALL="/tmp/ld-publish-package.tgz"
  rm -f "$TARBALL"
  yarn --cwd "./$workspace" pack -o "$TARBALL"

  # npm returns 403 when a version is already published. Tolerate this to allow
  # partial retries (matching the old yarn --tolerate-republish behavior).
  OUTPUT=$(npm publish "$TARBALL" --access public --provenance $TAG_ARGS 2>&1) || {
    if echo "$OUTPUT" | grep -q "You cannot publish over the previously published versions"; then
      echo "Already published $workspace, skipping."
    else
      echo "$OUTPUT" >&2
      echo "npm publish failed for $workspace" >&2
      rm -f "$TARBALL"
      exit 1
    fi
  }
  rm -f "$TARBALL"
done