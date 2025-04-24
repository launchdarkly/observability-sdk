#!/usr/bin/env bash
if $LD_RELEASE_IS_DRYRUN ; then
  echo "Doing a dry run of publishing."
else
  if $LD_RELEASE_IS_PRERELEASE ; then
    echo "Publishing with prerelease tag."
    yarn publish --tag prerelease || { echo "npm publish failed" >&2; exit 1; }
  else
    yarn publish || { echo "npm publish failed" >&2; exit 1; }
  fi
fi