#!/usr/bin/env bash
# Compiles core/ for Java 8 (the oldest target, Paper 1.12.2) and runs its automatic tests. No libraries needed.
set -euo pipefail
cd "$(dirname "$0")/../core"
rm -rf build/coretest && mkdir -p build/coretest
javac --release 8 -Xlint:-options -d build/coretest $(find src -name '*.java')
java -cp build/coretest com.cosmiclogic.deathbreakdown.core.CoreTest
