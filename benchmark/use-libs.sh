#!/usr/bin/env bash
# use-libs.sh — swap librapidsnark.so in rapidsnark/src/jniLibs/ between the
# canonical "old" and "new" copies stored under rapidsnark/src/libs/.
#
# Usage: ./benchmark/use-libs.sh <old|new>

set -euo pipefail

if [[ $# -ne 1 || ( "$1" != "old" && "$1" != "new" ) ]]; then
  echo "Usage: $0 <old|new>" >&2
  exit 2
fi

variant="$1"
here="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd -- "$here/.." && pwd)"

src_root="$root/rapidsnark/src/libs/$variant"
dst_root="$root/rapidsnark/src/jniLibs"

if [[ ! -d "$src_root" ]]; then
  echo "error: source dir missing: $src_root" >&2
  exit 1
fi

md5_of() {
  if command -v md5 >/dev/null; then
    md5 -q "$1"
  else
    md5sum "$1" | awk '{print $1}'
  fi
}

for abi in arm64-v8a x86_64; do
  src="$src_root/$abi/librapidsnark.so"
  dst_dir="$dst_root/$abi"
  dst="$dst_dir/librapidsnark.so"

  if [[ ! -f "$src" ]]; then
    echo "error: missing $src" >&2
    exit 1
  fi

  mkdir -p "$dst_dir"
  cp -f "$src" "$dst"
  size=$(wc -c < "$dst" | tr -d ' ')
  echo "  $abi  $(md5_of "$dst")  ${size} bytes  ($variant)"
done

echo
echo "Active lib variant: $variant"
echo "Destination: $dst_root/{arm64-v8a,x86_64}/librapidsnark.so"
