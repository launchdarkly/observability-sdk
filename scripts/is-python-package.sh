
#!/bin/bash

# Check if the specified directory contains a pyproject.toml file.
# ./scripts/is-python-package.sh packages/sdk/python
# Returns 0 (success) if pyproject.toml exists, 1 (failure) if it doesn't.

set -e

if [ -f "$1/pyproject.toml" ]; then
    exit 0
else
    exit 1
fi
