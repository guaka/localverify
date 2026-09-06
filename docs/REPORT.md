# Report contract, version 1

The authoritative fields, status meaning, campaign rules and compatibility requirements now live in the [report compatibility specification](../openspec/specs/report-compatibility/spec.md).

The production apps retain their existing serializers during the experiment. In particular, Android currently exports Gson epoch-millisecond dates while iOS exports ISO-8601. Existing case readers remain unchanged. See the [legacy difference table](ENGINE-EXPERIMENT.md) before assuming current cross-platform wire compatibility.

Reports contain investigation leads and coverage information. Including an original archive in a report ZIP remains an explicit user choice.
