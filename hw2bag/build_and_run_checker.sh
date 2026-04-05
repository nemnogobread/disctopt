#!/usr/bin/env bash
# Прогон чекера; общий лимит стеночного времени 30 с (SIGTERM по истечении).
set -euo pipefail
cd "$(dirname "$0")"
javac BagSolver.java BagSolverChecker.java
exec timeout --foreground 30s java BagSolverChecker
