#!/bin/sh
set -e
mkdir -p dist
for v in 1.21.1 1.21.4 1.21.5 1.21.8 1.21.11; do
  echo "===== Building for $v ====="
  ./gradlew dist -Pmc=$v
done
echo "Done. The jars are in the dist folder."
