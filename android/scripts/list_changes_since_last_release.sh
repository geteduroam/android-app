#!/bin/bash -ex

RELEASE_TYPE=$1
GIT_LIST_TAG_COMMAND="git tag -l --sort=-creatordate v*"
PREVIOUS_VERSION="$(${GIT_LIST_TAG_COMMAND} | head -n 1)"

function list_merged_pull_requests {
  git log ${PREVIOUS_VERSION}..HEAD --oneline --merges | \
    grep -E "Merge pull request" | \
    sed "s/.*android\/\(.*\)$/* \1/" | \
    sed "s/.* from \(.*\) to .*$/* \1/"
}

list_merged_pull_requests
