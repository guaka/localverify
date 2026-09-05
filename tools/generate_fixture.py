"""Generate an explicitly synthetic archive for manual Files/share-sheet tests."""
from pathlib import Path
import io
import tarfile

root = Path(__file__).resolve().parents[1]
destination = root / "Fixtures" / "synthetic-sysdiagnose.tar.gz"
with tarfile.open(destination, "w:gz", format=tarfile.USTAR_FORMAT) as archive:
    for name, text in {
        "synthetic-sysdiagnose/test.log": "SYNTHETIC TEST DATA ONLY\nConnected to triage-test.invalid\nOrdinary crash: no indicator\n",
        "synthetic-sysdiagnose/analytics.json": '{"hostname":"triage-test.invalid","timestamp":"2026-09-05T00:00:00Z"}',
    }.items():
        payload = text.encode()
        info = tarfile.TarInfo(name); info.size = len(payload); info.mtime = 0
        archive.addfile(info, io.BytesIO(payload))
print(destination)
