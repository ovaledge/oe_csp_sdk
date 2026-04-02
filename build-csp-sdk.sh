#!/bin/bash
# Build script for csp-sdk assembly (fat jar with all connectors)
# Usage: ./build-csp-sdk.sh
# Optional: CSP_JAR_OUTPUT_DIR=/path/to/jars ./build-csp-sdk.sh
#
# Examples:
#   ./build-csp-sdk.sh
#   CSP_JAR_OUTPUT_DIR=/path/to/jars ./build-csp-sdk.sh

echo '---------------------------'
echo 'Building csp-sdk assembly jar (with all connectors)'
echo '---------------------------'

mvn clean install -pl assembly -am
MVN_EXIT=$?
if [ "$MVN_EXIT" -ne 0 ]; then
    echo "Error: Maven build failed (exit code $MVN_EXIT)."
    exit 1
fi

# Find the assembly jar
JAR_FILE=$(find assembly/target -name "csp-sdk-*.jar" -type f ! -name "*-sources.jar" ! -name "*-javadoc.jar" ! -name "original-*.jar" 2>/dev/null | head -1)

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo "Error: Assembly jar not found in assembly/target/"
    echo "Hint: Run the script from the oe_csp_sdk root directory."
    exit 1
fi

JAR_NAME=$(basename "$JAR_FILE")
echo "Build successful: $JAR_NAME"
echo "JAR location: $JAR_FILE"
echo "JAR size: $(ls -lh "$JAR_FILE" | awk '{print $5}')"

# Copy to output directory if CSP_JAR_OUTPUT_DIR is set
if [ -n "$CSP_JAR_OUTPUT_DIR" ]; then
    JAR_DIR="$CSP_JAR_OUTPUT_DIR"
    mkdir -p "$JAR_DIR" || { echo "Error: Failed to create directory: $JAR_DIR"; exit 1; }
    cp "$JAR_FILE" "$JAR_DIR/" || { echo "Error: Failed to copy jar to $JAR_DIR"; exit 1; }
    echo "Jar copied to $JAR_DIR: $JAR_NAME"
fi
