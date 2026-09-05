# Derived confirmed-case fixture

`derived-confirmed-pegasus-sysdiagnose.tar.gz` is **synthetic test data**, not
a sysdiagnose acquired from a victim's device and not proof that any device is
compromised. It contains two minimal JSON `.ips` records whose process names
and timestamps are reconstructed from the public forensic timeline for the
anonymized French journalist `FRJRN2` in Amnesty International's 2021 Pegasus
methodology report.

The companion `derived-confirmed-pegasus.stix2` is fixture-only threat
intelligence. It must not be used to assess a device or represented as an
Amnesty, Citizen Lab, Apple, or MVT indicator bundle.

The fixture intentionally excludes raw device data, personal identifiers,
exploit attachments, URLs, payloads, and any claim of an original forensic
record. It is retained solely to verify that the app can produce reproducible,
structured leads from the shape of publicly described confirmed-compromise
traces.

Source: [Amnesty International, *Forensic Methodology Report: How to catch NSO
Group's Pegasus* (2021)](https://www.amnesty.org/en/latest/research/2021/07/forensic-methodology-report-how-to-catch-nso-groups-pegasus/), section 4.
