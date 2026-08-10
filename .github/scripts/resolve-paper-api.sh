#!/usr/bin/env bash
#
# Resolves the paper-api artifact version to target.
#
# The coordinate is READ from the repository's maven-metadata rather than
# constructed from a Minecraft version, because upstream has used two schemes:
#
#   pre-26.x : 1.21.11-R0.1-SNAPSHOT
#   26.x+    : 26.2.build.111-stable        (Minecraft's calendar versioning)
#
# Any rule that builds "<mc>-R0.1-SNAPSHOT" silently breaks the moment a release
# line changes shape, so the only safe source is what is actually published.
#
# Policies:
#   line    stay within the current release line (1.21.x -> newest 1.21.x).
#           The default: patch-level updates that rarely need code changes.
#   latest  newest stable line overall (1.21.x -> 26.2). A platform jump; must
#           be requested deliberately.
#
# Also reports the Java version the target requires. paper-api's Gradle module
# metadata declares `org.gradle.jvm.version`, and resolution fails outright when
# the toolchain is older -- Paper 26.x needs 25 while the 1.21 line needs 21. A
# platform bump that moves paper-api without moving the toolchain cannot even
# resolve its own compile classpath, so the two have to move together.
#
# Prints `current=<v>`, `target=<v>`, `updateType=<none|patch|line>`,
# `currentJdk=<n>`, `targetJdk=<n>` as KEY=VALUE lines suitable for
# $GITHUB_OUTPUT.

set -euo pipefail

POLICY="${1:-line}"
CATALOG="${CATALOG:-gradle/libs.versions.toml}"
PAPER_REPO="https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api"
METADATA_URL="$PAPER_REPO/maven-metadata.xml"

CURRENT="$(sed -n 's/^paper-api[[:space:]]*=[[:space:]]*"\(.*\)"[[:space:]]*$/\1/p' "$CATALOG" | head -n1)"
[[ -n "$CURRENT" ]] || { echo "could not read paper-api from $CATALOG" >&2; exit 1; }

# Minecraft version embedded in an artifact version, for either scheme.
mc_of() {
    local artifact="$1"
    if [[ "$artifact" =~ ^(.+)-R[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
        echo "${BASH_REMATCH[1]}"
    elif [[ "$artifact" =~ ^(.+)\.build\.[0-9]+-[A-Za-z]+$ ]]; then
        echo "${BASH_REMATCH[1]}"
    else
        return 1
    fi
}

# Release line: 1.21.11 -> 1.21, 26.2 -> 26.2, 26.1.1 -> 26.1
line_of() {
    echo "$1" | cut -d- -f1 | cut -d. -f1,2
}

CURRENT_MC="$(mc_of "$CURRENT")" || { echo "unrecognised current version: $CURRENT" >&2; exit 1; }
CURRENT_LINE="$(line_of "$CURRENT_MC")"

ALL="$(curl -fsSL "$METADATA_URL" \
    | grep -o '<version>[^<]*</version>' \
    | sed 's|</\?version>||g')"

# Drop anything that is not a stable release of a stable Minecraft version:
# -pre/-rc builds, the 26.x alpha/beta channels, and one-off forks such as
# 1.21.5-no-moonrise-SNAPSHOT.
STABLE="$(echo "$ALL" \
    | grep -Ev -- '-(pre[0-9]*|rc[0-9]*)-R' \
    | grep -Ev -- '\.build\.[0-9]+-(alpha|beta)$' \
    | grep -Ev -- '-no-[a-z]+-SNAPSHOT$')"

# maven-metadata lists versions oldest-first, so the last match is the newest.
if [[ "$POLICY" == "line" ]]; then
    CANDIDATES="$(echo "$STABLE" | grep -E "^${CURRENT_LINE//./\\.}(\.|-|\.build\.)" || true)"
else
    CANDIDATES="$STABLE"
fi

TARGET="$(echo "$CANDIDATES" | tail -n1)"
[[ -n "$TARGET" ]] || { echo "no candidate versions found for policy '$POLICY'" >&2; exit 1; }

TARGET_MC="$(mc_of "$TARGET")" || { echo "unrecognised target version: $TARGET" >&2; exit 1; }
TARGET_LINE="$(line_of "$TARGET_MC")"

if [[ "$TARGET" == "$CURRENT" ]]; then
    UPDATE_TYPE="none"
elif [[ "$TARGET_LINE" == "$CURRENT_LINE" ]]; then
    UPDATE_TYPE="patch"
else
    UPDATE_TYPE="line"
fi

# The Java version an artifact demands, read from its Gradle module metadata.
# Snapshots need the timestamped filename resolved from the version's own
# maven-metadata first. Several variants declare a version; take the highest.
required_jdk_for() {
    local artifact="$1" module_url timestamp

    if [[ "$artifact" == *-SNAPSHOT ]]; then
        timestamp="$(curl -fsSL "$PAPER_REPO/$artifact/maven-metadata.xml" 2>/dev/null \
            | grep -o '<value>[^<]*' | head -n1 | sed 's|<value>||')" || return 0
        [[ -n "$timestamp" ]] || return 0
        module_url="$PAPER_REPO/$artifact/paper-api-$timestamp.module"
    else
        module_url="$PAPER_REPO/$artifact/paper-api-$artifact.module"
    fi

    curl -fsSL "$module_url" 2>/dev/null \
        | grep -o '"org\.gradle\.jvm\.version"[[:space:]]*:[[:space:]]*[0-9]\+' \
        | grep -o '[0-9]\+$' \
        | sort -rn | head -n1
}

CURRENT_JDK="$(sed -n 's/^jdk[[:space:]]*=[[:space:]]*"\(.*\)"[[:space:]]*$/\1/p' "$CATALOG" | head -n1)"
[[ -n "$CURRENT_JDK" ]] || { echo "could not read jdk from $CATALOG" >&2; exit 1; }

REQUIRED_JDK="$(required_jdk_for "$TARGET" || true)"

# Never propose lowering the toolchain: a newer Paper needing an older JDK is
# not a reason to downgrade, and an unreadable module file must not silently
# rewrite a working configuration.
if [[ -n "$REQUIRED_JDK" ]] && (( REQUIRED_JDK > CURRENT_JDK )); then
    TARGET_JDK="$REQUIRED_JDK"
else
    TARGET_JDK="$CURRENT_JDK"
fi

cat <<EOF
current=$CURRENT
currentMinecraft=$CURRENT_MC
target=$TARGET
targetMinecraft=$TARGET_MC
targetLine=$TARGET_LINE
updateType=$UPDATE_TYPE
currentJdk=$CURRENT_JDK
requiredJdk=${REQUIRED_JDK:-unknown}
targetJdk=$TARGET_JDK
EOF
