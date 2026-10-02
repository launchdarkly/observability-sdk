#!/bin/bash
# Publishes a placeholder package to npmjs so that OIDC trusted publishing
# can be configured. See contributing/publishing.md for details.
#
# npm's trusted publishing (OIDC) can only be configured on a package that
# already exists. A brand-new @launchdarkly/* package therefore fails its
# first CI publish with a 404 (PUT .../@launchdarkly%2f<pkg> - Not found).
# Run this once to establish the package, then configure trusted publishing.
#
# Usage:
#   ./scripts/publish-placeholder-package.sh sdk/@launchdarkly/observability-next

set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <workspace-path>"
  echo "Example: $0 sdk/@launchdarkly/observability-next"
  exit 1
fi

WORKSPACE_PATH="$1"

if [ ! -f "$WORKSPACE_PATH/package.json" ]; then
  echo "Error: $WORKSPACE_PATH/package.json not found"
  exit 1
fi

PACKAGE_NAME=$(./scripts/package-name.sh "$WORKSPACE_PATH")
echo "Publishing placeholder for: $PACKAGE_NAME"

# We must ensure that we are not publishing a placeholder to a package that already
# exists on npm.
if npm view "$PACKAGE_NAME" --json &>/dev/null; then
  echo "Package $PACKAGE_NAME already exists on npm. Skipping placeholder publish."
  exit 0
fi

TEMP_DIR=$(mktemp -d)

cleanup() {
  echo "Cleaning up temp directory..."
  rm -rf "$TEMP_DIR"
  echo "Logging out of npm..."
  npm logout 2>/dev/null || true
}
trap cleanup EXIT

echo "Logging in to npm..."
npm login

cat > "$TEMP_DIR/package.json" <<EOF
{
  "name": "$PACKAGE_NAME",
  "version": "0.0.0",
  "description": ""
}
EOF

# Publish under the 'snapshot' tag so the empty placeholder does not become
# the 'latest' version that consumers would install.
echo "Publishing $PACKAGE_NAME@0.0.0 with tag 'snapshot'..."
npm publish --tag snapshot --access public "$TEMP_DIR"

echo ""
echo "Successfully published $PACKAGE_NAME@0.0.0"
echo ""
echo "Next steps:"
echo "  1. Configure trusted publishing on npmjs:"
echo "     https://docs.npmjs.com/trusted-publishers#configuring-trusted-publishing"
echo "     Publisher:          GitHub Actions"
echo "     Organization:       launchdarkly"
echo "     Repository:         observability-sdk"
echo "     Workflow filename:  turbo.yml"
echo "  2. Mark the package as public on npmjs"
echo "  3. Re-run the failed Monorepo publish (push to main or re-run the job)"
