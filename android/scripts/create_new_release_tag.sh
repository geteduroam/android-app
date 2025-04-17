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
git tag -a "v${VERSION}(${BUILD_NUMBER})" -m "$(cat "${RELEASE_NOTES_FILE}")"

# Create F-Droid release notes
FDROID_CHANGELOG_FILE_GETEDUROAM="android/app/src/eduroam/play/release-notes/en-US/default.txt"
FDROID_CHANGELOG_FILE_GETGOVROAM="android/app/src/govroam/play/release-notes/en-US/default.txt"
cp "${RELEASE_NOTES_FILE}" "$FDROID_CHANGELOG_FILE_GETEDUROAM"
cp "${RELEASE_NOTES_FILE}" "$FDROID_CHANGELOG_FILE_GETGOVROAM"
git add "$FDROID_CHANGELOG_FILE_GETEDUROAM"
git add "$FDROID_CHANGELOG_FILE_GETGOVROAM"
git commit -m "Add F-Droid changelog for ${RELEASE_GIT_TAG}"

git push -u origin main
