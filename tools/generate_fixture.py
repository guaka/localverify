"""Generate explicitly synthetic archives for manual Files/share-sheet tests."""
from pathlib import Path
import io
import tarfile

root = Path(__file__).resolve().parents[1]
fixtures = {
    "synthetic-sysdiagnose.tar.gz": {
        "synthetic-sysdiagnose/test.log": "SYNTHETIC TEST DATA ONLY\nConnected to triage-test.invalid\nOrdinary crash: no indicator\n",
        "synthetic-sysdiagnose/analytics.json": '{"hostname":"triage-test.invalid","timestamp":"2026-09-05T00:00:00Z"}',
    },
    "derived-confirmed-pegasus-sysdiagnose.tar.gz": {
        "derived-confirmed-case/crashes_and_spins/roleaccountd.ips": '{"procName":"roleaccountd","timestamp":"2019-08-16T12:41:36Z"}',
        "derived-confirmed-case/crashes_and_spins/stagingd.ips": '{"procName":"stagingd","timestamp":"2019-08-16T12:41:52Z"}',
        "derived-confirmed-case/README.txt": "DERIVED TEST DATA ONLY — not a device acquisition or exploit payload.\nPublicly reported process names and timestamps are minimally reconstructed for parser testing.\n",
    },
}
for filename, records in fixtures.items():
    destination = root / "Fixtures" / filename
    with tarfile.open(destination, "w:gz", format=tarfile.USTAR_FORMAT) as archive:
        for name, text in records.items():
            payload = text.encode()
            info = tarfile.TarInfo(name); info.size = len(payload); info.mtime = 0
            archive.addfile(info, io.BytesIO(payload))
    print(destination)
