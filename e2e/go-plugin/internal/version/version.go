package version

import _ "embed"

//go:generate sh -c "printf %s $(git rev-parse HEAD) > internal/version/VERSION.txt"
//go:embed VERSION.txt
var Commit string
