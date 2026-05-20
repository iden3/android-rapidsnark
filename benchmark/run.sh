#!/usr/bin/env bash
# run.sh — run the RapidsnarkBenchmarkTest against a connected device/emulator
# for both the "old" and "new" native lib variants, then aggregate results
# into benchmark/results/comparison.md.
#
# Usage:
#   ./benchmark/run.sh                           # full run, old + new
#   ./benchmark/run.sh --variants new            # only the "new" variant
#   ./benchmark/run.sh --testdata /path/to/data  # override testdata source
#   ./benchmark/run.sh --circuit authV2,linkedMultiQuery10   # filter (comma-separated substrings, OR)
#   ./benchmark/run.sh --force-push                          # re-upload testdata even if present
#   ./benchmark/run.sh --skip-push                           # don't push testdata (assume present)
#   ./benchmark/run.sh --iterations 3                        # override iteration count
#   ./benchmark/run.sh --cooldown 180                        # sleep N sec between variants (thermal)
#   ./benchmark/run.sh --circuit-cooldown 30                 # uniform on-device cooldown before each circuit
#   ./benchmark/run.sh --small-cooldown 3 --big-cooldown 10  # per-size cooldowns (big if zkey >= threshold)
#   ./benchmark/run.sh --big-threshold 50                    # MB threshold for "big". Default 50.

set -euo pipefail

here="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd -- "$here/.." && pwd)"
cd "$root"

APP_ID="com.example.android_rapidsnark"
TEST_PACKAGE="${APP_ID}.test"
TEST_CLASS="com.example.rapidsnark_example.RapidsnarkBenchmarkTest"
RUNNER="androidx.test.runner.AndroidJUnitRunner"

DEVICE_BASE="/sdcard/Android/data/${APP_ID}/files"
DEVICE_TESTDATA="${DEVICE_BASE}/testdata2"
DEVICE_RESULTS="${DEVICE_BASE}/benchmark_results"

TESTDATA_SRC="/Users/moria/development/testdata2"
VARIANTS=("old" "new")
CIRCUIT_FILTER=""
ITERATIONS=""
FORCE_PUSH=0
SKIP_PUSH=0
COOLDOWN_BETWEEN_VARIANTS=0
COOLDOWN_BETWEEN_CIRCUITS=0
COOLDOWN_SMALL=""
COOLDOWN_BIG=""
BIG_THRESHOLD_MB=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --testdata)          TESTDATA_SRC="$2"; shift 2 ;;
    --variants)          IFS=',' read -r -a VARIANTS <<< "$2"; shift 2 ;;
    --circuit)           CIRCUIT_FILTER="$2"; shift 2 ;;
    --iterations)        ITERATIONS="$2"; shift 2 ;;
    --force-push)        FORCE_PUSH=1; shift ;;
    --skip-push)         SKIP_PUSH=1; shift ;;
    --cooldown)          COOLDOWN_BETWEEN_VARIANTS="$2"; shift 2 ;;
    --circuit-cooldown)  COOLDOWN_BETWEEN_CIRCUITS="$2"; shift 2 ;;
    --small-cooldown)    COOLDOWN_SMALL="$2"; shift 2 ;;
    --big-cooldown)      COOLDOWN_BIG="$2"; shift 2 ;;
    --big-threshold)     BIG_THRESHOLD_MB="$2"; shift 2 ;;
    -h|--help)
      grep -E '^# ' "$0" | sed 's/^# //'; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

# --- preflight -------------------------------------------------------------

if ! command -v adb >/dev/null; then
  echo "error: adb not on PATH" >&2; exit 1
fi

devices=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ -z "$devices" ]]; then
  echo "error: no authorized adb device. Connect one and try again." >&2
  exit 1
fi
primary_device=$(echo "$devices" | head -1)
abi=$(adb -s "$primary_device" shell getprop ro.product.cpu.abi | tr -d '\r')
echo "device: $primary_device  abi: $abi"

# --- testdata push --------------------------------------------------------

if [[ "$SKIP_PUSH" -eq 0 ]]; then
  if [[ ! -d "$TESTDATA_SRC" ]]; then
    echo "error: TESTDATA_SRC not a directory: $TESTDATA_SRC" >&2; exit 1
  fi

  already_present=$(adb -s "$primary_device" shell "ls $DEVICE_TESTDATA 2>/dev/null | wc -l" | tr -d '\r')
  if [[ "$already_present" -gt 0 && "$FORCE_PUSH" -eq 0 ]]; then
    echo "testdata already on device at $DEVICE_TESTDATA ($already_present entries). Skipping push. (use --force-push to re-upload)"
  else
    echo "pushing testdata: $TESTDATA_SRC  →  $DEVICE_TESTDATA ..."
    adb -s "$primary_device" shell "mkdir -p $DEVICE_TESTDATA"
    adb -s "$primary_device" push "$TESTDATA_SRC/." "$DEVICE_TESTDATA/" >/dev/null
    # On Android 11+, adb push into the app's scoped-storage sandbox
    # (/sdcard/Android/data/<pkg>/) creates files owned by `shell`. The app
    # process can't enumerate them via java.io.File.listFiles despite being in
    # the same ext_data_rw group. Relaxing the mode bits side-steps the FUSE
    # ownership-enforcement layer so the app sees the tree.
    adb -s "$primary_device" shell "chmod -R a+rwx $DEVICE_TESTDATA" >/dev/null 2>&1 || true
    echo "push complete."
  fi
fi

mkdir -p benchmark/results

# --- per-variant build + test ----------------------------------------------

variant_idx=0
for variant in "${VARIANTS[@]}"; do
  if [[ "$variant" != "old" && "$variant" != "new" ]]; then
    echo "error: unknown variant '$variant' (expected old|new)" >&2; exit 2
  fi

  if [[ "$variant_idx" -gt 0 && "$COOLDOWN_BETWEEN_VARIANTS" -gt 0 ]]; then
    echo
    echo "cooling down ${COOLDOWN_BETWEEN_VARIANTS}s between variants ..."
    sleep "$COOLDOWN_BETWEEN_VARIANTS"
  fi
  variant_idx=$((variant_idx + 1))

  echo
  echo "======================================================================"
  echo "  variant: $variant"
  echo "======================================================================"

  ./benchmark/use-libs.sh "$variant"

  echo "building app + androidTest apk..."
  ./gradlew :app:installDebug :app:installDebugAndroidTest

  # clear previous CSVs on device for this variant so pulled filenames are unambiguous
  adb -s "$primary_device" shell "rm -f $DEVICE_RESULTS/${variant}_*.csv 2>/dev/null || true"

  extra_args=("-e" "libVariant" "$variant")
  if [[ -n "$CIRCUIT_FILTER" ]]; then
    extra_args+=("-e" "circuitFilter" "$CIRCUIT_FILTER")
  fi
  if [[ -n "$ITERATIONS" ]]; then
    extra_args+=("-e" "iterations" "$ITERATIONS")
  fi
  if [[ "$COOLDOWN_BETWEEN_CIRCUITS" -gt 0 ]]; then
    extra_args+=("-e" "circuitCooldownSec" "$COOLDOWN_BETWEEN_CIRCUITS")
  fi
  if [[ -n "$COOLDOWN_SMALL" ]]; then
    extra_args+=("-e" "smallCircuitCooldownSec" "$COOLDOWN_SMALL")
  fi
  if [[ -n "$COOLDOWN_BIG" ]]; then
    extra_args+=("-e" "bigCircuitCooldownSec" "$COOLDOWN_BIG")
  fi
  if [[ -n "$BIG_THRESHOLD_MB" ]]; then
    extra_args+=("-e" "bigThresholdMb" "$BIG_THRESHOLD_MB")
  fi

  echo "running instrumentation (variant=$variant)..."
  adb -s "$primary_device" shell am instrument -w -r \
    "${extra_args[@]}" \
    -e class "$TEST_CLASS" \
    "${TEST_PACKAGE}/${RUNNER}"

  echo "pulling results..."
  device_stage="benchmark/results/device_${variant}"
  rm -rf "$device_stage"
  mkdir -p "$device_stage"
  adb -s "$primary_device" pull "$DEVICE_RESULTS" "$device_stage" >/dev/null
  # `adb pull <dir> <dest>` copies <dir> itself into <dest>, so CSVs land under
  # $device_stage/benchmark_results/ — search recursively to be safe.
  latest=$(find "$device_stage" -type f -name "${variant}_*.csv" -exec ls -t {} + 2>/dev/null | head -1 || true)
  if [[ -n "$latest" ]]; then
    cp "$latest" "benchmark/results/$(basename "$latest")"
    echo "  → benchmark/results/$(basename "$latest")"
  else
    echo "warning: no CSV produced for variant=$variant" >&2
  fi
done

# --- summary ---------------------------------------------------------------

echo
echo "======================================================================"
echo "  summary"
echo "======================================================================"

old_csv=$(ls -t benchmark/results/old_*.csv 2>/dev/null | head -1 || true)
new_csv=$(ls -t benchmark/results/new_*.csv 2>/dev/null | head -1 || true)

if [[ -n "$old_csv" && -n "$new_csv" ]]; then
  ./benchmark/summarize.sh "$old_csv" "$new_csv" "$abi" > benchmark/results/comparison.md
  echo "wrote benchmark/results/comparison.md"
  echo
  cat benchmark/results/comparison.md
else
  echo "skipping comparison (need both old and new CSVs; old='$old_csv' new='$new_csv')"
fi
