#!/bin/sh
# Standard Gradle wrapper launch script.
# NOTE: gradle-wrapper.jar (binary) is not bundled in this scaffold — see README
# "First-time setup" for how to generate it. Opening this project in Android
# Studio will also offer to do this automatically.

DEFAULT_JVM_OPTS=""
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec java $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
