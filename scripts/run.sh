#!/bin/bash
# Executa o Job Scanner

set -e

cd "$(dirname "$0")/.."

echo "🔨 Building..."
./mvnw package -DskipTests -q

echo "🚀 Running Job Scanner..."
java -jar target/*.jar "$@"
