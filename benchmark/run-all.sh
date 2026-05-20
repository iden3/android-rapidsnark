#!/usr/bin/env bash
# run-all.sh — one-shot: run both variants over every circuit in testdata2 and
# produce a timestamped chart-only comparison file. No conclusions, just data.
#
# Usage:
#   ./benchmark/run-all.sh                           # default: 5 iter, 20 s between variants
#   ITERATIONS=10 ./benchmark/run-all.sh             # more iterations per circuit
#   COOLDOWN=90 ./benchmark/run-all.sh               # longer between-variant cooldown
#   ./benchmark/run-all.sh --skip-push               # reuse existing testdata on device
#   ./benchmark/run-all.sh --variants new            # re-run only the "new" variant
#
# Output:
#   benchmark/results/comparison_<UTC-timestamp>.md   (one doc, one table, 20 circuits)
#   benchmark/results/{old,new}_<timestamp>.csv       (raw per-iteration data)

set -euo pipefail

here="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$here/.."

STAMP=$(date -u +"%Y%m%d-%H%M%S")

./benchmark/run.sh \
  --cooldown "${COOLDOWN:-20}" \
  --small-cooldown 3 \
  --big-cooldown 10 \
  --big-threshold 50 \
  --iterations "${ITERATIONS:-5}" \
  "$@"

if [[ -f benchmark/results/comparison.md ]]; then
  out="benchmark/results/comparison_${STAMP}.md"
  mv benchmark/results/comparison.md "$out"
  echo
  echo "wrote $out"
else
  echo "warning: benchmark/results/comparison.md not produced — summary step may have failed." >&2
  exit 1
fi
