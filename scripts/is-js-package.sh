#!/bin/bash

# Check if the specified directory contains a package.json file.
# ./scripts/is-js-package.sh packages/sdk/server-node
# Returns 0 (success) if package.json exists, 1 (failure) if it doesn't.

set -e

if [ -f "$1/package.json" ]; then
    exit 0
else
    exit 1
fi 