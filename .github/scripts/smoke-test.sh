#!/usr/bin/env bash
#
# Boots a real Paper server with the shaded LoafyLib jar and asserts the plugin
# enables cleanly.
#
# Why this exists: `gradle build` cannot catch the failure modes this plugin is
# actually exposed to.
#
#   * shadowJar's minimize() strips classes that are only reached by reflection
#     or ServiceLoader (Lettuce, InvUI/NMS) — compiles fine, dies on class load
#   * relocation mistakes only surface when a relocated class is initialised
#     (e.g. Lettuce's DefaultClientResources static initialiser)
#   * Paper's library loader resolves its dependencies from Maven at boot, so a
#     bad coordinate in the generated manifest is invisible until startup
#   * a Kotlin stdlib skew between compile time and the library loader throws
#     NoSuchMethodError at runtime and nowhere else
#
# Every one of those produces a green build and a broken server, which is why
# no dependency or Minecraft bump is allowed to merge without passing this.
#
# It also installs PacketEvents, because LoafyLib's optional integrations never
# execute when the plugin they hook is missing -- see the soft dependency block
# below.
#
# Usage:
#   .github/scripts/smoke-test.sh [--jar <path>] [--mc <version>] [--dir <workdir>]
#                                 [--no-soft-deps]
#
# Defaults: newest jar in build/libs, Minecraft version from
# `./gradlew printMinecraftVersion`, work dir .smoke/

set -euo pipefail

JAR=""
MC_VERSION=""
WORK_DIR=".smoke"
WITH_SOFT_DEPS=1
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-300}"
SHUTDOWN_TIMEOUT="${SHUTDOWN_TIMEOUT:-90}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --jar) JAR="$2"; shift 2 ;;
        --mc) MC_VERSION="$2"; shift 2 ;;
        --dir) WORK_DIR="$2"; shift 2 ;;
        --no-soft-deps) WITH_SOFT_DEPS=0; shift ;;
        -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31mFAIL: %s\033[0m\n' "$*" >&2; exit 1; }

# Runs on Linux and macOS (and WSL). It is not supported under Git Bash on
# Windows: the console FIFO below is an MSYS emulation that a native Windows
# JVM cannot read from. Use `./gradlew runServer` for local testing there.
for tool in curl jq java mkfifo sha256sum; do
    command -v "$tool" >/dev/null 2>&1 || fail "required tool not found: $tool"
done

# -----------------------------------------------------------------------------
# Inputs
# -----------------------------------------------------------------------------
if [[ -z "$MC_VERSION" ]]; then
    MC_VERSION="$(./gradlew -q printMinecraftVersion --console=plain | tail -n 1 | tr -d '\r')"
fi
[[ -n "$MC_VERSION" ]] || fail "could not determine the Minecraft version"

if [[ -z "$JAR" ]]; then
    JAR="$(ls -t build/libs/*.jar 2>/dev/null | grep -v -- '-sources\|-javadoc' | head -n 1 || true)"
fi
[[ -n "$JAR" && -f "$JAR" ]] || fail "no plugin jar found — run ./gradlew build first"

log "Smoke testing $(basename "$JAR") against Paper $MC_VERSION"

# -----------------------------------------------------------------------------
# Resolve and download the Paper server jar (fill v3 API)
# -----------------------------------------------------------------------------
CACHE_DIR="${PAPER_CACHE_DIR:-$HOME/.cache/paper}"
mkdir -p "$CACHE_DIR"

BUILD_JSON="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds/latest")" \
    || fail "no Paper build published for Minecraft ${MC_VERSION}"

PAPER_BUILD="$(jq -r '.id' <<<"$BUILD_JSON")"
PAPER_URL="$(jq -r '.downloads["server:default"].url' <<<"$BUILD_JSON")"
PAPER_SHA="$(jq -r '.downloads["server:default"].checksums.sha256' <<<"$BUILD_JSON")"
PAPER_JAR="$CACHE_DIR/paper-${MC_VERSION}-${PAPER_BUILD}.jar"

[[ "$PAPER_URL" != "null" && -n "$PAPER_URL" ]] || fail "could not resolve a download URL for Paper ${MC_VERSION}"

if [[ ! -f "$PAPER_JAR" ]]; then
    log "Downloading Paper ${MC_VERSION} build ${PAPER_BUILD}"
    curl -fsSL -o "$PAPER_JAR.tmp" "$PAPER_URL"
    mv "$PAPER_JAR.tmp" "$PAPER_JAR"
fi

# Verifying the checksum matters here: this script runs inside the automated
# update workflows, so an unverified download would be an unattended remote
# artifact executed on a runner holding repository credentials.
ACTUAL_SHA="$(sha256sum "$PAPER_JAR" | cut -d' ' -f1)"
[[ "$ACTUAL_SHA" == "$PAPER_SHA" ]] \
    || fail "checksum mismatch for $PAPER_JAR (expected $PAPER_SHA, got $ACTUAL_SHA)"

# -----------------------------------------------------------------------------
# Prepare a throwaway server directory
# -----------------------------------------------------------------------------
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/plugins"
cp "$JAR" "$WORK_DIR/plugins/"

# -----------------------------------------------------------------------------
# Soft dependencies
#
# LoafyLib's optional integrations stay inert when the plugin they hook is
# absent, so a server running LoafyLib alone never executes them at all.
# PacketEvents is the one that matters: the glowing service and camera
# animation are built on its protocol mappings, which are Minecraft-version
# specific. A PacketEvents predating the target platform breaks both features
# while a LoafyLib-only boot still passes -- which is exactly how v1.2.0 shipped
# claiming 26.2 support with those features broken.
#
# Installing it makes that path initialise, so a mismatch fails here instead.
# The version comes from the catalog, like everything else.
# -----------------------------------------------------------------------------
PACKETEVENTS_VERSION=""
if (( WITH_SOFT_DEPS == 1 )); then
    PACKETEVENTS_VERSION="$(sed -n 's/^packetevents[[:space:]]*=[[:space:]]*"\(.*\)"[[:space:]]*$/\1/p' \
        gradle/libs.versions.toml | head -n1)"
fi

if [[ -n "$PACKETEVENTS_VERSION" ]]; then
    PE_JAR="$CACHE_DIR/packetevents-spigot-${PACKETEVENTS_VERSION}.jar"
    PE_URL="https://repo.codemc.io/repository/maven-releases/com/github/retrooper/packetevents-spigot/${PACKETEVENTS_VERSION}/packetevents-spigot-${PACKETEVENTS_VERSION}.jar"

    if [[ ! -f "$PE_JAR" ]]; then
        log "Downloading PacketEvents ${PACKETEVENTS_VERSION}"
        curl -fsSL -o "$PE_JAR.tmp" "$PE_URL" \
            || fail "could not download PacketEvents ${PACKETEVENTS_VERSION} from $PE_URL"
        mv "$PE_JAR.tmp" "$PE_JAR"
    fi

    cp "$PE_JAR" "$WORK_DIR/plugins/"
    log "Installed PacketEvents ${PACKETEVENTS_VERSION} as a soft dependency"
fi

# Accepting the EULA on a disposable CI server directory, per Mojang's terms.
echo "eula=true" > "$WORK_DIR/eula.txt"

cat > "$WORK_DIR/server.properties" <<'EOF'
online-mode=false
level-type=flat
spawn-protection=0
max-players=1
view-distance=4
simulation-distance=4
sync-chunk-writes=false
enable-jmx-monitoring=false
EOF

LOG_FILE="$WORK_DIR/boot.log"

# -----------------------------------------------------------------------------
# Boot, wait for startup, then stop cleanly
# -----------------------------------------------------------------------------
log "Booting server (timeout ${STARTUP_TIMEOUT}s)"

CONSOLE="$WORK_DIR/console.fifo"
mkfifo "$CONSOLE"

(
    cd "$WORK_DIR"
    exec java -Xms1G -Xmx2G -XX:+UseG1GC -jar "$PAPER_JAR" --nogui
) < "$CONSOLE" > "$LOG_FILE" 2>&1 &
SERVER_PID=$!

# Holding the write end open keeps the server's stdin from hitting EOF, which
# Paper treats as an immediate shutdown request.
exec 3>"$CONSOLE"

cleanup() {
    # Braces, not a bare `exec ... 2>/dev/null`: an exec carrying only
    # redirections applies them to the shell permanently, which would send the
    # rest of this script's stderr — including every failure message — to
    # /dev/null. The group scopes the redirect to this one command while still
    # closing the descriptor in the current shell.
    { exec 3>&-; } 2>/dev/null || true
    if kill -0 "$SERVER_PID" 2>/dev/null; then
        kill -9 "$SERVER_PID" 2>/dev/null || true
    fi
    rm -f "$CONSOLE"
}
trap cleanup EXIT

STARTED=0
elapsed=0
while (( elapsed < STARTUP_TIMEOUT )); do
    if grep -q 'Done (.*)! For help, type "help"' "$LOG_FILE" 2>/dev/null; then
        STARTED=1
        break
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        break
    fi
    sleep 2
    elapsed=$(( elapsed + 2 ))
done

if (( STARTED == 1 )); then
    log "Server reached startup in ${elapsed}s — stopping"
    echo "stop" >&3 || true
    waited=0
    while kill -0 "$SERVER_PID" 2>/dev/null && (( waited < SHUTDOWN_TIMEOUT )); do
        sleep 2
        waited=$(( waited + 2 ))
    done
fi

exec 3>&- 2>/dev/null || true
wait "$SERVER_PID" 2>/dev/null || true
trap - EXIT
rm -f "$CONSOLE"

# -----------------------------------------------------------------------------
# Assertions
# -----------------------------------------------------------------------------
log "Checking boot log"

PLUGIN_NAME="$(grep -m1 '^name:' src/main/resources/paper-plugin.yml | sed 's/^name:[[:space:]]*//' | tr -d '\r')"

if (( STARTED == 0 )); then
    echo "----- last 120 log lines -----" >&2
    tail -n 120 "$LOG_FILE" >&2 || true
    fail "server did not finish starting within ${STARTUP_TIMEOUT}s"
fi

# The library loader logs resolution problems without aborting startup, so an
# explicit scan is required — a server that boots is not a server that loaded us.
FATAL_PATTERNS=(
    'NoClassDefFoundError'
    'ClassNotFoundException'
    'NoSuchMethodError'
    'NoSuchFieldError'
    'LinkageError'
    'IncompatibleClassChangeError'
    'ExceptionInInitializerError'
    'Could not resolve'
    'Error resolving librar'
    'Failed to resolve'
    "Could not load '"
    'Error occurred while enabling'
    'Error initializing plugin'
)

FOUND_FATAL=0
for pattern in "${FATAL_PATTERNS[@]}"; do
    if grep -n "$pattern" "$LOG_FILE" >/dev/null 2>&1; then
        echo "" >&2
        echo "matched fatal pattern: $pattern" >&2
        grep -n -A 15 "$pattern" "$LOG_FILE" | head -n 40 >&2
        FOUND_FATAL=1
    fi
done
(( FOUND_FATAL == 0 )) || fail "classloading or library-loader errors during boot"

# Plugin-lifecycle assertions run against the STARTUP portion of the log only.
# Stopping the server disables every plugin, so "Disabling <plugin>" is normal
# at shutdown and only means something if it appears before startup completed.
STARTUP_LOG="$WORK_DIR/startup.log"
sed -n '1,/Done (.*)! For help, type "help"/p' "$LOG_FILE" > "$STARTUP_LOG"

grep -q "Enabling ${PLUGIN_NAME}" "$STARTUP_LOG" \
    || fail "${PLUGIN_NAME} was never enabled — check that the jar landed in plugins/"

# Paper disables a plugin whose onEnable threw, so a disable *before* startup
# finished catches a plugin that logged "Enabling" and then failed.
if grep -q "Disabling ${PLUGIN_NAME}" "$STARTUP_LOG"; then
    grep -n -B 20 "Disabling ${PLUGIN_NAME}" "$STARTUP_LOG" | tail -n 40 >&2
    fail "${PLUGIN_NAME} was disabled during startup"
fi

# Installing PacketEvents is only worth anything if it actually came up and
# LoafyLib bound to it. If either half fails, the integration is silently
# inert -- the plugin still enables and the boot still looks clean.
if [[ -n "$PACKETEVENTS_VERSION" ]]; then
    if ! grep -qi "Enabling packetevents" "$STARTUP_LOG"; then
        grep -n -i "packetevents" "$STARTUP_LOG" | head -n 20 >&2
        fail "PacketEvents ${PACKETEVENTS_VERSION} did not enable on Paper ${MC_VERSION}"
    fi

    if grep -q "PacketEvents not found" "$STARTUP_LOG"; then
        grep -n "infrastructure ready" "$STARTUP_LOG" >&2 || true
        fail "PacketEvents ${PACKETEVENTS_VERSION} was installed but ${PLUGIN_NAME} did not detect it"
    fi

    log "PacketEvents ${PACKETEVENTS_VERSION} enabled and detected"
fi

log "PASS — ${PLUGIN_NAME} enabled on Paper ${MC_VERSION} build ${PAPER_BUILD}"
