#!/usr/bin/env sh
set -eu

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    exec java -Xmx2048m -jar "$WRAPPER_JAR" "$@"
elif command -v gradle >/dev/null 2>&1; then
    exec gradle --project-dir "$APP_HOME" "$@"
else
    echo "Error: Neither gradle/wrapper/gradle-wrapper.jar nor a system 'gradle' installation was found." >&2
    echo "Open the 'android/' folder in Android Studio (which auto-provisions Gradle 8.11.1)," >&2
    echo "or install Gradle 8.11.1 and run: gradle wrapper --gradle-version 8.11.1" >&2
    exit 1
fi
