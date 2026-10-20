#!/bin/bash
# Gradle wrapper helper: pins JDK 25 and a project-local Gradle home.
HERE="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="$HERE/jdk/jdk-25.0.4.1+1"
export GRADLE_USER_HOME="$HERE/.gradle-home"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$HERE" && ./gradlew "$@"
