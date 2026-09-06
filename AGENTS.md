# Design guidance

- For all iOS interface and interaction work, follow [Apple’s Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/).

# Diagnostic data handling

- When debugging the app or gathering debug data, never copy actual sysdiagnose archives or their extracted contents from the user's phone onto the MacBook. Keep that evidence on the phone.
- Use generated synthetic fixtures and non-content progress/timing information for debugging. Do not download the app's evidence container, originals, or case exports to investigate a problem. Ask for explicit permission before any exception; ordinary debugging or installation requests do not authorize evidence transfer.

# Specifications and future changes

- `openspec/specs/` is authoritative for specified behavior. Future-engine requirements are migration targets; consult `docs/ENGINE-EXPERIMENT.md` for legacy differences.
- For behavioral changes, use an OpenSpec proposal, scenarios, design, and tasks; update synthetic acceptance tests with the implementation and archive only after verification. Routine internal cleanups need no proposal.
- After archiving, replace any generated purpose placeholders and run strict validation again.
- Invoke the pinned, telemetry-disabled CLI with `npm run spec -- <arguments>`; validate with `npm run spec:validate`.
- Keep experimental engine dependencies outside production targets. Do not weaken the production offline-policy checker to admit experiments.
