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
  npm publish "./$workspace" --access public --provenance $TAG_ARGS || { echo "npm publish failed for $workspace" >&2; exit 1; }
done