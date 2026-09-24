#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/field-service-avatar-test-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" \
  src/main/java/dev/infrai/fieldservice/*.java \
  src/test/java/dev/infrai/fieldservice/*.java
java -cp "$BUILD_DIR" dev.infrai.fieldservice.AvatarPipelineServiceTest
