#!/usr/bin/env bash
# summarize.sh — emit a per-iteration markdown comparison of two CSVs.
#
# Output columns, per circuit, from left to right:
#   circuit, zkey MB, old i1..iN, old mean±σ, new i1..iN, new mean±σ, Δ mean, speedup
#
# Usage: ./benchmark/summarize.sh <old.csv> <new.csv> [abi]

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <old.csv> <new.csv> [abi]" >&2
  exit 2
fi

old_csv="$1"
new_csv="$2"
abi="${3:-unknown}"

if [[ ! -f "$old_csv" ]]; then echo "missing: $old_csv" >&2; exit 1; fi
if [[ ! -f "$new_csv" ]]; then echo "missing: $new_csv" >&2; exit 1; fi

timestamp=$(date -u +"%Y-%m-%d %H:%M:%SZ")

awk -v abi="$abi" -v timestamp="$timestamp" -v old_csv="$old_csv" -v new_csv="$new_csv" '
BEGIN {
  FS = ","
}

function ingest(file, label,    line, hdr, circuit, iter, proveMs, zkeyBytes, key) {
  hdr = 1
  while ((getline line < file) > 0) {
    if (hdr) { hdr = 0; continue }
    if (line == "") continue
    n = split(line, f, ",")
    if (n < 7) continue
    circuit  = f[2]
    iter     = f[3] + 0
    proveMs  = f[5] + 0
    zkeyBytes = f[7] + 0
    if (iter < 1) continue   # skip error rows (-1)

    circuits[circuit] = 1
    if (!(circuit in zkey) || zkey[circuit] == 0) zkey[circuit] = zkeyBytes

    key = label SUBSEP circuit SUBSEP iter
    val[key] = proveMs
    if (iter > max_iter[label,circuit]) max_iter[label,circuit] = iter
    if (iter > global_max_iter) global_max_iter = iter

    have[label,circuit] = 1
  }
  close(file)
}

function mean_of(label, c,     i, n, s) {
  n = max_iter[label,c]
  if (n <= 0) return -1
  s = 0
  for (i = 1; i <= n; i++) s += val[label SUBSEP c SUBSEP i]
  return s / n
}
function std_of(label, c, m,     i, n, s, d) {
  n = max_iter[label,c]
  if (n <= 1) return 0
  s = 0
  for (i = 1; i <= n; i++) { d = val[label SUBSEP c SUBSEP i] - m; s += d * d }
  return sqrt(s / (n - 1))
}

END {
  ingest(old_csv, "old")
  ingest(new_csv, "new")

  # Sorted union of circuits.
  n_all = 0
  for (c in circuits) { n_all++; names[n_all] = c }
  for (i = 2; i <= n_all; i++) {
    k = names[i]; j = i - 1
    while (j > 0 && names[j] > k) { names[j+1] = names[j]; j-- }
    names[j+1] = k
  }

  N = global_max_iter   # number of iteration columns to emit

  print "# rapidsnark native lib benchmark — old vs new (per-iteration)"
  print ""
  print "- generated: " timestamp
  print "- device ABI: `" abi "`"
  print "- old CSV: `" old_csv "`"
  print "- new CSV: `" new_csv "`"
  print "- iteration columns: 1.." N " (prove-only, milliseconds). **mean** column includes ±1σ."
  print "- speedup = old_mean / new_mean (>1 ⇒ new is faster)."
  print ""

  # --- header -----------------------------------------------------------
  header = "| Circuit | zkey MB |"
  sep    = "|---|---:|"
  for (i = 1; i <= N; i++) { header = header " old i" i " |"; sep = sep "---:|" }
  header = header " **old mean** |"; sep = sep "---:|"
  for (i = 1; i <= N; i++) { header = header " new i" i " |"; sep = sep "---:|" }
  header = header " **new mean** | Δ mean | speedup |"
  sep    = sep    "---:|---:|---:|"
  print header
  print sep

  # --- per-circuit rows -------------------------------------------------
  log_speedup_sum = 0; speedup_count = 0

  for (r = 1; r <= n_all; r++) {
    c = names[r]
    zMB = zkey[c] / 1048576.0
    row = sprintf("| %s | %.0f |", c, zMB)

    # old iterations
    if (("old",c) in have) {
      n_old = max_iter["old",c]
      for (i = 1; i <= N; i++) {
        v = val["old" SUBSEP c SUBSEP i]
        if (i <= n_old) row = row sprintf(" %.0f |", v)
        else            row = row " — |"
      }
      om = mean_of("old", c); os = std_of("old", c, om)
      row = row sprintf(" **%.1f ± %.1f** |", om, os)
    } else {
      for (i = 1; i <= N; i++) row = row " — |"
      row = row " — |"
      om = -1
    }

    # new iterations
    if (("new",c) in have) {
      n_new = max_iter["new",c]
      for (i = 1; i <= N; i++) {
        v = val["new" SUBSEP c SUBSEP i]
        if (i <= n_new) row = row sprintf(" %.0f |", v)
        else            row = row " — |"
      }
      nm = mean_of("new", c); ns = std_of("new", c, nm)
      row = row sprintf(" **%.1f ± %.1f** |", nm, ns)
    } else {
      for (i = 1; i <= N; i++) row = row " — |"
      row = row " — |"
      nm = -1
    }

    if (om > 0 && nm > 0) {
      delta   = nm - om
      speedup = om / nm
      row     = row sprintf(" %+.0f | %.2f× |", delta, speedup)
      if (speedup > 0) { log_speedup_sum += log(speedup); speedup_count++ }
    } else {
      row = row " — | — |"
    }

    print row
  }

  print ""
  if (speedup_count > 0) {
    geomean = exp(log_speedup_sum / speedup_count)
    printf("**Geometric-mean prove-time speedup across %d shared circuits: %.3f×**\n",
      speedup_count, geomean)
    if (geomean > 1.0) {
      printf("(new libs are on average %.1f%% faster.)\n", (geomean - 1.0) * 100.0)
    } else {
      printf("(new libs are on average %.1f%% slower.)\n", (1.0 - geomean) * 100.0)
    }
  } else {
    print "_No circuit was present in both runs; cannot compute geomean speedup._"
  }
}
' < /dev/null
