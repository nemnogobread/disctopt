#!/usr/bin/env bash

set -euo pipefail

javac SetCoverSolver.java SetCoverSolverChecker.java

java SetCoverSolverChecker

