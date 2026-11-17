#!/usr/bin/env bash
# Usage: nuget-needs-publish.sh <dir>
# Finds the .nupkg files under <dir> (ignoring .symbols.nupkg) and checks
# nuget.org for each package id + version. Writes publish=true to
# $GITHUB_OUTPUT if at least one of them is not published yet, else
# publish=false. The push step is gated on this so that main pushes that do
# not bump a package version do not re-push (and authenticate against
# nuget.org with NUGET_API_KEY) for nothing.
set -euo pipefail

dir="${1:?usage: $0 <dir>}"
publish=false
found=0

while IFS= read -r -d '' pkg; do
	name="$(basename "$pkg" .nupkg)"
	# <Id>.<Version>.nupkg: the version starts at the first all-digit segment
	# that is followed by another digit (e.g. Highlight.ASP4.0.2.10).
	if [[ ! "$name" =~ ^([^.]+(\.[^.]*[^0-9.][^.]*)*)\.([0-9]+\.[0-9].*)$ ]]; then
		echo "::error::cannot parse package id and version from $pkg"
		exit 1
	fi
	found=$((found + 1))
	id="${BASH_REMATCH[1],,}"
	version="${BASH_REMATCH[3],,}"
	index="$(mktemp)"
	status="$(curl -sS -o "$index" -w '%{http_code}' "https://api.nuget.org/v3-flatcontainer/${id}/index.json")"
	if [[ "$status" == 200 ]] && grep -q "\"${version}\"" "$index"; then
		echo "::notice::${id} ${version} is already on nuget.org, nothing to publish"
	elif [[ "$status" == 200 || "$status" == 404 ]]; then
		echo "::notice::${id} ${version} is not on nuget.org yet, it will be published"
		publish=true
	else
		echo "::error::nuget.org returned HTTP ${status} for ${id}"
		exit 1
	fi
	rm -f "$index"
done < <(find "$dir" -name '*.nupkg' ! -name '*.symbols.nupkg' -print0)

if [[ "$found" -eq 0 ]]; then
	echo "::error::no .nupkg found under $dir"
	exit 1
fi

echo "publish=${publish}" >>"${GITHUB_OUTPUT:-/dev/stdout}"
