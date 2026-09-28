import SwiftUI
import MaKeebCompanion

@main
struct MaKeebApp: App {
    var body: some Scene {
        WindowGroup {
            CompanionView()
                // UIKit's tab bar controller and Compose handle safe areas and the keyboard inset.
                .ignoresSafeArea()
        }
    }
}

/// Hosts the companion app: a native tab bar around the shared Compose screens (setup, settings, try it).
struct CompanionView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
