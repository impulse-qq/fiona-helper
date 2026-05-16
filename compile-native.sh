#!/bin/bash
set -e

GRAALVM_HOME="/usr/lib/jvm/graalvm-jdk-25.0.3+9.1"

if [ ! -d "$GRAALVM_HOME" ]; then
    echo "Error: GraalVM not found at $GRAALVM_HOME"
    exit 1
fi

export JAVA_HOME="$GRAALVM_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using GraalVM: $JAVA_HOME"
java -version

echo ""
echo "Building native image..."
mvn clean package -Pnative -DskipTests "$@"

echo ""
echo "Native image built successfully:"
ls -lh target/*-runner
