package version

import _ "embed"

// Commit is the commit hash of the current version.
//
//go:generate sh -c "printf %s $(git rev-parse HEAD) > internal/version/VERSION.txt"
//go:embed VERSION.txt
var Commit string
