#!/bin/sh
set -e
mkdir -p dist
for v in 26.1.2 26.2 26.3; do
  echo "===== Building for $v ====="
  ./gradlew dist -Pmc=$v
done
echo "Done. The jars are in the dist folder."
