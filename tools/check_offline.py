"""Conservative source-policy check, not a runtime firewall or security proof.

Run with python3 tools/check_offline.py; --self-test exercises synthetic violations.
New frameworks/dependencies require reviewing this policy before adding them.
References stored as URL strings are allowed; explicit export sharing and Apple's
AccessibilitySettings shortcut are allowed. No real diagnostic data is inspected.
"""
from pathlib import Path
import plistlib
import os
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ALLOWED_IMPORTS = {
    "Foundation", "CryptoKit", "CZlib", "SwiftUI", "UIKit",
    "UniformTypeIdentifiers", "Accessibility",
}
FORBIDDEN = re.compile(
    r"\b(?:URLSession\w*|NSURLSession\w*|NSURLConnection|URLRequest|NSURLRequest|"
    r"URLProtocol|WKWebView|UIWebView|SFSafariViewController|NWConnection\w*|"
    r"NWListener|CFHTTP\w*|CFReadStreamCreateFor\w*|CFWriteStreamCreateFor\w*|"
    r"SCNetwork\w*|getaddrinfo|socket|connect|sendto|recvfrom)\b"
    r"|\b(?:Link|openURL)\s*\("
    r"|\bUIApplication\s*\.\s*shared\s*\.\s*open\b"
    r"|\b(?:InputStream|OutputStream|Stream)\s*\([^\n]*(?:url|host)\s*:"
)


def source_issues(text):
    issues = []
    for number, line in enumerate(text.splitlines(), 1):
        # Deliberately lexical: even commented-out APIs trigger review.
        if FORBIDDEN.search(line):
            issues.append((number, "network/browser API requires offline-policy review"))
        for module in re.findall(r"^\s*(?:@\w+\s+)*import\s+(?:class\s+|struct\s+|func\s+)?(\w+)", line):
            if module not in ALLOWED_IMPORTS:
                issues.append((number, f"unreviewed import: {module}"))
    return issues


def check(root):
    errors = []
    def traversal_error(error):
        raise error  # Never silently skip an unreadable source directory.
    for directory in ("Sources", "iOS/App", "iOS/Share"):
        for parent, _, files in os.walk(root / directory, onerror=traversal_error):
            for name in sorted(files):
                path = Path(parent) / name
                if path.suffix in {".swift", ".h", ".c", ".m", ".mm", ".cpp"}:
                    errors.extend(f"{path}:{line}: error: {message}"
                                  for line, message in source_issues(path.read_text()))
    for name in ("App", "Share"):
        path = root / f"iOS/{name}-Info.plist"
        ats = plistlib.loads(path.read_bytes()).get("NSAppTransportSecurity", {})
        if any(value not in (False, {}) for value in ats.values()):
            errors.append(f"{path}: error: ATS exceptions require offline-policy review")
    manifest = root / "Package.swift"
    if re.search(r"\.package\s*\(", manifest.read_text()):
        errors.append(f"{manifest}: error: package dependencies require offline-policy review")
    for name in ("LocalVerify", "LocalVerifyLocal"):
        path = root / f"{name}.xcodeproj/project.pbxproj"
        if not path.exists():
            continue
        if re.search(r"XCRemoteSwiftPackageReference|XCLocalSwiftPackageReference|XCSwiftPackageProductDependency|wrapper\.framework|wrapper\.xcframework", path.read_text()):
            errors.append(f"{path}: error: added dependencies require offline-policy review")
    return errors


def self_test():
    for text in ("URLSession.shared", "import Network", "import WebKit",
                 'Link("Help", destination: url)', 'openURL(url)',
                 'UIApplication.shared.open(url)', 'socket(0, 0, 0)',
                 'import Alamofire', 'InputStream(url: url)'):
        assert source_issues(text), text
    for text in ('Text(verbatim: "https://example.invalid")',
                 'ShareLink("Export", item: file)', 'import Foundation',
                 'AccessibilitySettings.openSettings(for: .assistiveTouch)',
                 'Data(contentsOf: localFileURL)'):
        assert not source_issues(text), text
    print("Offline policy synthetic checks passed.")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif sys.argv[1:]:
        sys.exit("Usage: python3 tools/check_offline.py [--self-test]")
    else:
        failures = check(ROOT)
        if failures:
            sys.exit("\n".join(failures))
        print("Offline source policy passed (not a runtime network block).")
