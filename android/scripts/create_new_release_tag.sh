#!/bin/bash -ex

source android/scripts/constants.sh

RELEASE_TYPE="$1"
VERSION=$(cat "${VERSION_FILE}")
BUILD_NUMBER=$(cat "${BUILD_NUMBER_FILE}")

RELEASE_GIT_TAG="${VERSION}-${RELEASE_TYPE}-${BUILD_NUMBER}"

mkdir -p "$(dirname $RELEASE_NOTES_FILE)" && touch "$RELEASE_NOTES_FILE"
echo "Release notes for version $VERSION ($BUILD_NUMBER)" > $RELEASE_NOTES_FILE
echo "================================" >> $RELEASE_NOTES_FILE
android/scripts/list_changes_since_last_release.sh "${RELEASE_TYPE}" >> "${RELEASE_NOTES_FILE}"

git config user.name "github-actions"
git config user.email "github-actions@github.com"
git tag -a "v${VERSION}" -m "$(cat "${RELEASE_NOTES_FILE}")"

# Create F-Droid release notes
FDROID_CHANGELOG_FILE="metadata/en-US/changelogs/${BUILD_NUMBER}.txt"
mv "${RELEASE_NOTES_FILE}" "$FDROID_CHANGELOG_FILE"
git add "$FDROID_CHANGELOG_FILE"
git commit -m "Add F-Droid changelog for ${RELEASE_GIT_TAG}"
