"""Check or refresh attributed offline indicators; no device data is accessed."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import urllib.parse
import urllib.request

FILES = {"pegasus": "2021-07-18_nso/pegasus.stix2", "cytrox": "2021-12-16_cytrox/cytrox.stix2"}
MAX_BYTES = 5 * 1024 * 1024
SOURCE = "https://github.com/AmnestyTech/investigations"

def fetch(url):
    with urllib.request.urlopen(url, timeout=30) as response:
        if response.url != url:
            raise ValueError("Unexpected publisher redirect")
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("Publisher file exceeds 5 MiB")
    return data

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", default="master", help="Branch/tag/commit, resolved to an immutable commit (default: master)")
    parser.add_argument("--check", action="store_true", help="Compare revisions without changing files")
    args = parser.parse_args()
    destination = Path(__file__).resolve().parents[1] / "iOS/App/ThreatData"
    manifest_file = destination / "threat-manifest.json"
    commit = json.loads(fetch("https://api.github.com/repos/AmnestyTech/investigations/commits/" + urllib.parse.quote(args.revision, safe="")))
    revision = commit["sha"]
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Invalid publisher revision")
    previous = json.loads(manifest_file.read_text()) if manifest_file.exists() else {}
    print("Bundled revision:", previous.get("revision", "not recorded"))
    print("Publisher revision:", revision)
    if args.check:
        print("Up to date" if previous.get("revision") == revision else "Refresh available")
        return
    payloads = {}
    manifest = dict(revision=revision, downloadedAt=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), source=SOURCE, license="CC BY 2.0", files=[])
    for name, path in FILES.items():
        url = f"https://raw.githubusercontent.com/AmnestyTech/investigations/{revision}/{path}"
        data = fetch(url)
        bundle = json.loads(data)
        if bundle.get("type") != "bundle" or not any(item.get("type") == "indicator" for item in bundle.get("objects", [])):
            raise ValueError(f"{name}: invalid indicator bundle; existing snapshot retained")
        digest = hashlib.sha256(data).hexdigest()
        payloads[name + ".stix2"] = data
        manifest["files"].append(dict(name=name, path=path, bytes=len(data), sha256=digest, url=url))
        print(name, len(data), digest)
    attribution = f"Indicators by Amnesty International. {SOURCE}\nCC BY 2.0: https://creativecommons.org/licenses/by/2.0/\nUnmodified source bundles; Local Verify uses a supported subset. No endorsement implied.\nRevision: {revision}\nRetrieved: {manifest['downloadedAt']}\n"
    attribution += "\n".join(f"{f['path']}\nSHA-256 {f['sha256']}\n{f['url']}" for f in manifest["files"])
    payloads["ATTRIBUTION.txt"] = attribution.encode()
    payloads["threat-manifest.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    destination.mkdir(exist_ok=True)
    # Download and validate all responses before replacing any bundled file.
    with tempfile.TemporaryDirectory(prefix="threat-update-", dir=destination.parent) as staging:
        for name, data in payloads.items():
            (Path(staging) / name).write_bytes(data)
        for name in payloads:
            os.replace(Path(staging) / name, destination / name)
    print("Snapshot refreshed. Verify with: swift test --scratch-path build/review-tests")
    print("Rebuild the iOS app to include the refreshed snapshot.")

if __name__ == "__main__":
    main()
