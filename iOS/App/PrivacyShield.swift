import SwiftUI
import UIKit

/// Cover the window synchronously before UIKit captures a background snapshot.
struct PrivacyShield: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        context.coordinator.anchor = view
        return view
    }
    func updateUIView(_ view: UIView, context: Context) {
        if UIApplication.shared.applicationState != .active { context.coordinator.cover() }
    }
    final class Coordinator: NSObject {
        weak var anchor: UIView?
        private var shield: UIView?
        override init() {
            super.init()
            NotificationCenter.default.addObserver(self, selector: #selector(cover), name: UIApplication.willResignActiveNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(cover), name: UIApplication.didEnterBackgroundNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(uncover), name: UIApplication.didBecomeActiveNotification, object: nil)
        }
        @objc func cover() {
            guard shield == nil, let window = anchor?.window else { return }
            let view = UIView(frame: window.bounds)
            view.backgroundColor = .systemBackground
            view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.accessibilityViewIsModal = true
            view.isAccessibilityElement = true
            view.accessibilityLabel = "Local Verify — contents hidden"
            window.addSubview(view)
            shield = view
        }
        @objc func uncover() { shield?.removeFromSuperview(); shield = nil }
        deinit { NotificationCenter.default.removeObserver(self); shield?.removeFromSuperview() }
    }
}
