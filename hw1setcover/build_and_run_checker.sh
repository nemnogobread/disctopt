#!/usr/bin/env bash

set -euo pipefail

# Compile SetCoverSolver and SetCoverSolverChecker
javac SetCoverSolver.java SetCoverSolverChecker.java

# Run (deploy) the checker
java SetCoverSolverChecker

