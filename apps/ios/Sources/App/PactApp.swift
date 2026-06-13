import SwiftUI
import UIKit

@main
struct PactApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(model.client)
                .environmentObject(model.auth)
                .task {
                    appDelegate.model = model
                    model.bootstrap()
                }
                .onOpenURL { url in
                    model.handle(url: url)
                }
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                model.auth.refresh()
                // We may have missed live frames while backgrounded; reconcile
                // the shield off the shared state the push handler maintained.
                model.reconcileShieldFromSharedState()
                if model.client.credentials != nil {
                    model.client.connect()
                }
            default:
                break
            }
        }
    }
}
