#!/bin/bash

echo "================================================================================"
echo "                    RETRO-CODER UI - Java Edition"
echo "================================================================================"
echo ""

# Check Java installation
if ! command -v java &> /dev/null; then
    echo "ERROR: Java not found. Please install Java 21 or later."
    echo "Download from: https://adoptium.net/"
    exit 1
fi

# Check Maven installation
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven not found. Please install Maven 3.9 or later."
    echo "Download from: https://maven.apache.org/download.cgi"
    exit 1
fi

# Build if JAR doesn't exist
if [ ! -f "target/RetroCoder.jar" ]; then
    echo "Building project..."
    mvn clean package -DskipTests -q
    if [ $? -ne 0 ]; then
        echo "ERROR: Build failed."
        exit 1
    fi
    echo "Build complete."
    echo ""
fi

echo "Starting server on http://localhost:8888"
echo "Press Ctrl+C to stop."
echo ""

# Run server mode (no --cli flag)
java -jar target/autocoder-java.jar
