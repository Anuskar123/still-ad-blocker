import SwiftUI
import NetworkExtension

@main
struct StillApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

@MainActor
final class DNSModel: ObservableObject {
    @Published var busy = false
    @Published var installed = false
    @Published var enabled = false
    @Published var message = "Checking DNS settings..."
    @Published var error: String?
    private let manager = NEDNSSettingsManager.shared()

    private func load() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            manager.loadFromPreferences { error in
                if let error = error { continuation.resume(throwing: error) }
                else { continuation.resume() }
            }
        }
    }

    func run(_ operation: String) async {
        guard !busy else { return }
        busy = true
        error = nil
        defer { busy = false }
        do {
            try await load()
            if operation == "install" {
                let settings = NEDNSOverHTTPSSettings(servers: ["94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff"])
                settings.serverURL = URL(string: "https://dns.adguard-dns.com/dns-query")!
                settings.matchDomains = [""]
                manager.dnsSettings = settings
                manager.localizedDescription = "Still - AdGuard Public DNS"
                manager.onDemandRules = [NEOnDemandRuleConnect()]
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    manager.saveToPreferences { error in
                        if let error = error { continuation.resume(throwing: error) }
                        else { continuation.resume() }
                    }
                }
            } else if operation == "remove" {
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    manager.removeFromPreferences { error in
                        if let error = error { continuation.resume(throwing: error) }
                        else { continuation.resume() }
                    }
                }
            }
            try await load()
            installed = manager.dnsSettings != nil
            enabled = manager.isEnabled
            message = enabled ? "Still DNS is enabled in system settings."
                : installed ? "Configuration saved. Select Still in your system DNS settings to enable it."
                : "Still DNS is not configured."
        } catch {
            self.error = error.localizedDescription
            message = "Could not verify DNS settings."
            enabled = false
        }
    }
}

struct ContentView: View {
    @StateObject private var model = DNSModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Still").font(.largeTitle.bold())
                Text("Encrypted filtering DNS").font(.title2)
                Text(model.message).font(.headline).accessibilityAddTraits(.updatesFrequently)
                if model.busy { ProgressView("Updating settings") }
                Text("Use AdGuard Public DNS to filter known advertising and tracking domains. DNS queries handled by this configuration are sent to AdGuard over HTTPS.")
                VStack(alignment: .leading, spacing: 12) {
                    Button("Save DNS configuration") { Task { await model.run("install") } }
                    Button("Remove app DNS configuration") { Task { await model.run("remove") } }
                        .disabled(!model.installed)
                    Button("Refresh status") { Task { await model.run("refresh") } }
                }.disabled(model.busy)
                if let error = model.error {
                    Text(error).foregroundColor(.red).textSelection(.enabled)
                }
                Text("Enable in system settings").font(.headline)
                #if os(iOS)
                Text("After saving, open Settings, then General, VPN & Device Management, DNS, and select Still. Return here to refresh the status. Menu names can vary by OS version.")
                #else
                Text("After saving, open System Settings and find the DNS configuration under Network or VPN. Select Still there, then return here to refresh. On older macOS versions, use System Preferences.")
                #endif
                Text("What this version covers").font(.headline)
                Text("This companion uses AdGuard's public blocklists. Android custom rules, downloaded lists, private browsing and request counters are not included. Apps using their own DNS or some VPN configurations can bypass it. YouTube video ads are not reliably blocked.")
                Text("Still does not record your DNS queries. AdGuard processes queries under its own privacy policy. Encrypted DNS does not hide your IP address or encrypt all internet traffic.")
                Link("Read AdGuard Public DNS privacy policy", destination: URL(string: "https://adguard-dns.io/en/privacy.html")!)
                Text("If you installed the separate Still configuration profile, manage or remove that profile in system settings. This app cannot report or remove a separately installed profile.").font(.footnote)
            }
            .padding(28)
            .frame(maxWidth: 640, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        #if os(macOS)
        .frame(minWidth: 480, minHeight: 600)
        #endif
        .task { await model.run("refresh") }
        .onChange(of: scenePhase) { phase in
            if phase == .active { Task { await model.run("refresh") } }
        }
    }
}
