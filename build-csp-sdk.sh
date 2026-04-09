#!/bin/bash
# Build script for csp-sdk assembly (fat jar) or csp-api (REST gateway jar).
#
# Usage:
#   ./build-csp-sdk.sh [assembly|api]
#
# Examples:
#   ./build-csp-sdk.sh              # same as assembly (default)
#   ./build-csp-sdk.sh assembly
#   ./build-csp-sdk.sh api

TARGET="${1:-assembly}"
case "$TARGET" in
    assembly|api) ;;
    *)
        echo "Usage: $0 [assembly|api]"
        echo "  assembly  — build oe-csp-sdk assembly fat jar (all connectors)"
        echo "  api       — build csp-api Spring Boot / shaded jar"
        exit 1
        ;;
esac

echo '---------------------------'
if [ "$TARGET" = "assembly" ]; then
    echo 'Building csp-sdk assembly jar (with all connectors)'
    MODULE="assembly"
    JAR_GLOB="csp-sdk-*.jar"
    TARGET_DIR="assembly/target"
else
    echo 'Building csp-api jar'
    MODULE="csp-api"
    JAR_GLOB="csp-api-*.jar"
    TARGET_DIR="csp-api/target"
fi
echo '---------------------------'

mvn clean install -pl "$MODULE" -am
MVN_EXIT=$?
if [ "$MVN_EXIT" -ne 0 ]; then
    echo "Error: Maven build failed (exit code $MVN_EXIT)."
    exit 1
fi

JAR_FILE=$(find "$TARGET_DIR" -name "$JAR_GLOB" -type f ! -name "*-sources.jar" ! -name "*-javadoc.jar" ! -name "original-*.jar" 2>/dev/null | head -1)

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo "Error: Jar not found in $TARGET_DIR/ (pattern: $JAR_GLOB)"
    echo "Hint: Run the script from the oe_csp_sdk root directory."
    exit 1
fi

JAR_NAME=$(basename "$JAR_FILE")
echo "Build successful: $JAR_NAME"
echo "JAR location: $JAR_FILE"
echo "JAR size: $(ls -lh "$JAR_FILE" | awk '{print $5}')"
