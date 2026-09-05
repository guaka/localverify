#!/usr/bin/env bash
# Download EC-DIGIT's public SAF iOS 15 sysdiagnose test archive.
# The result is intentionally stored in ignored TestData/, not committed.
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
destination_dir="${1:-$root_dir/TestData/sysdiagnose-saf}"
archive_name="ios15-sysdiagnose.tar.gz"
archive_path="$destination_dir/$archive_name"
lfs_batch_url="https://github.com/EC-DIGIT-CSIRC/sysdiagnose-testdata.git/info/lfs/objects/batch"
expected_sha256="4491d5e4b6f4349311df3b3fc671f1dd040c8ccda9f97e3a0debef151e613114"
object_size="93899558"

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }
command -v gzip >/dev/null || { echo "gzip is required" >&2; exit 1; }

mkdir -p "$destination_dir"
object_sha256="$expected_sha256"

download_url=$(printf '{"operation":"download","transfers":["basic"],"objects":[{"oid":"%s","size":%s}]}' "$object_sha256" "$object_size" |
    curl --fail --silent --show-error --location --request POST "$lfs_batch_url" \
        --header 'Accept: application/vnd.git-lfs+json' \
        --header 'Content-Type: application/vnd.git-lfs+json' \
        --data-binary @- |
    python3 -c 'import json, sys; print(json.load(sys.stdin)["objects"][0]["actions"]["download"]["href"])')

temporary_path="$(mktemp "$destination_dir/.${archive_name}.XXXXXX")"
trap 'rm -f "$temporary_path"' EXIT
curl --fail --show-error --location --retry 3 --output "$temporary_path" "$download_url"

actual_sha256=$(openssl dgst -sha256 -r "$temporary_path" | awk '{print $1}')
if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    echo "Checksum mismatch: expected $expected_sha256, got $actual_sha256" >&2
    exit 1
fi
gzip -t "$temporary_path"
mv -f "$temporary_path" "$archive_path"
trap - EXIT

echo "Downloaded and verified: $archive_path"
echo "Run the real-archive smoke test with:"
echo "TRIAGE_REAL_SYSDIAGNOSE='$archive_path' swift test --filter TriageCoreTests/testRealSysdiagnoseWhenProvided"
