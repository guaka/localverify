# Design guidance

- For all iOS interface and interaction work, follow [Apple’s Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/).

# Diagnostic data handling

- When debugging the app or gathering debug data, never copy actual sysdiagnose archives or their extracted contents from the user's phone onto the MacBook. Keep that evidence on the phone.
- Use generated synthetic fixtures and non-content progress/timing information for debugging. Do not download the app's evidence container, originals, or case exports to investigate a problem. Ask for explicit permission before any exception; ordinary debugging or installation requests do not authorize evidence transfer.
