#!/bin/bash
# Executa o Job Scanner em modo dry-run (sem enviar email)

set -e

cd "$(dirname "$0")/.."

echo "🔨 Building..."
./mvnw package -DskipTests -q

echo "🧪 Running Job Scanner (DRY-RUN mode)..."
DRY_RUN=true java -jar target/*.jar "$@"
