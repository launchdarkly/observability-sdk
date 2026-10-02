import SwiftUI

enum ContentTab: String, Hashable {
    case welcome, home, settings
}

struct ContentView: View {
    @AppStorage("tab") var tab = ContentTab.welcome
    @AppStorage("name") var welcomeName = "Skipper"
    @AppStorage("appearance") var appearance = ""
    @State var viewModel = ViewModel()

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack {
                WelcomeView(welcomeName: $welcomeName)
            }
            .tabItem { Label("Welcome", systemImage: "heart.fill") }
            .tag(ContentTab.welcome)

            NavigationStack {
                ItemListView()
                    .navigationTitle(Text("\(viewModel.items.count) Items"))
            }
            .tabItem { Label("Home", systemImage: "house.fill") }
            .tag(ContentTab.home)

            NavigationStack {
                SettingsView(appearance: $appearance, welcomeName: $welcomeName)
                    .navigationTitle("Settings")
            }
            .tabItem { Label("Settings", systemImage: "gearshape.fill") }
            .tag(ContentTab.settings)
        }
        .environment(viewModel)
        .preferredColorScheme(appearance == "dark" ? .dark : appearance == "light" ? .light : nil)
    }
}

struct WelcomeView : View {
    @State var heartBeating = false
    @Binding var welcomeName: String

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("Hello [\(welcomeName)](https://skip.dev)!")
                    .font(.largeTitle)
                Image(systemName: "heart.fill")
                    .font(.largeTitle)
                    .foregroundStyle(.red)
                    .scaleEffect(heartBeating ? 1.5 : 1.0)
                    .task {
                        withAnimation(.easeInOut(duration: 1).repeatForever()) {
                            heartBeating = true
                        }
                    }

                Divider().padding(.horizontal, 40)

                FlagsView()
                Divider().padding(.horizontal, 40)
                ObserveView()

                if let built = BuildInfo.displayString {
                    Text("Compiled \(built)")
                        .font(.caption2.monospaced())
                        .foregroundStyle(.secondary)
                }
            }
            .padding()
        }
    }
}

/// Every typed flag evaluation, using the same call on iOS and Android.
struct FlagsView : View {
    @State var boolValue = false
    @State var intValue = 0
    @State var doubleValue = 0.0
    @State var stringValue = ""
    @State var jsonValue = "{}"

    var body: some View {
        VStack(spacing: 8) {
            Text("Flags")
                .font(.title2.weight(.semibold))
            FlagRow(key: LaunchDarklyConfig.featureFlagKey, value: boolValue ? "ON" : "OFF")
            FlagRow(key: LaunchDarklyConfig.intFlagKey, value: "\(intValue)")
            FlagRow(key: LaunchDarklyConfig.doubleFlagKey, value: "\(doubleValue)")
            FlagRow(key: LaunchDarklyConfig.stringFlagKey, value: stringValue)
            FlagRow(key: LaunchDarklyConfig.jsonFlagKey, value: jsonValue)
            Text("Every per-key variation records a deduplicated `flag_exposure` span.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            HStack {
                Button("Evaluate all") {
                    evaluate()
                }
                Button("Evaluate twice") {
                    evaluate()
                    evaluate()
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .task {
            // Give the client a moment to connect, then read the flags.
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            evaluate()
        }
    }

    func evaluate() {
        boolValue = LaunchDarklyFlags.boolVariation(LaunchDarklyConfig.featureFlagKey, defaultValue: false)
        intValue = LaunchDarklyFlags.intVariation(LaunchDarklyConfig.intFlagKey, defaultValue: -1)
        doubleValue = LaunchDarklyFlags.doubleVariation(LaunchDarklyConfig.doubleFlagKey, defaultValue: -1.5)
        stringValue = LaunchDarklyFlags.stringVariation(LaunchDarklyConfig.stringFlagKey, defaultValue: "fallback")
        let json = LaunchDarklyFlags.jsonVariation(LaunchDarklyConfig.jsonFlagKey, defaultValue: ["fallback": true])
        jsonValue = JSONBridge.string(from: json)
    }
}

struct FlagRow : View {
    let key: String
    let value: String

    var body: some View {
        HStack {
            Text(key)
                .font(.footnote.weight(.medium))
            Spacer()
            Text(value)
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }
}

/// Manual OpenTelemetry signals: iOS records only what is pressed here, while Android
/// adds its default automatic instrumentation on top.
struct ObserveView : View {
    @State var lastSignal = "nothing recorded yet"
    @State var identifyCount = 0

    var body: some View {
        VStack(spacing: 8) {
            Text("Observability")
                .font(.title2.weight(.semibold))
            Text(lastSignal)
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            HStack {
                Button("Log") {
                    LaunchDarklyObserve.recordLog(
                        "howdy-log",
                        severity: .info,
                        properties: ["source": "welcome-tab", "attempt": 1]
                    )
                    note("recordLog(severity: .info)")
                }
                Button("Error") {
                    LaunchDarklyObserve.recordError("howdy-error", cause: "demo button")
                    note("recordError(cause:)")
                }
                Button("Span") {
                    LaunchDarklyObserve.recordSpan("howdy-span", properties: ["kind": "demo"]) {
                        Thread.sleep(forTimeInterval: 0.05)
                    }
                    note("recordSpan(properties:)")
                }
            }

            HStack {
                Button("Metric") {
                    LaunchDarklyObserve.recordMetric("howdy.gauge", value: 42)
                    note("recordMetric(42)")
                }
                Button("Count") {
                    LaunchDarklyObserve.recordCount("howdy.count", value: 3)
                    note("recordCount(3)")
                }
                Button("Incr") {
                    LaunchDarklyObserve.recordIncr("howdy.incr")
                    note("recordIncr(1)")
                }
            }

            HStack {
                Button("Histogram") {
                    LaunchDarklyObserve.recordHistogram("howdy.histogram", value: 12.5)
                    note("recordHistogram(12.5)")
                }
                Button("Up/Down") {
                    LaunchDarklyObserve.recordUpDownCounter("howdy.updown", value: -2)
                    note("recordUpDownCounter(-2)")
                }
            }

            HStack {
                Button("Screen view") {
                    LaunchDarklyObserve.trackScreenView("Welcome", properties: ["tab": "welcome"])
                    note("trackScreenView(Welcome)")
                }
                Button("Track event") {
                    LaunchDarklyFlags.track("howdy-track", metricValue: 1)
                    note("LDClient.track(howdy-track)")
                }
            }

            // Guarded-rollout error metric: track occurrence + flush so it is not left
            // in the 30s buffer if the app is killed (message-to-customer-flagsdk.md).
            Button("Track app-error") {
                LaunchDarklyFlags.track("app-error", data: [
                    "screen": "checkout",
                    "error_type": "network"
                ])
                LaunchDarklyFlags.flush()
                note("track(app-error) + flush()")
            }

            Button("Identify new context") {
                identifyCount += 1
                let contextKey = "\(LaunchDarklyConfig.contextKey)-\(identifyCount)"
                LaunchDarklyFlags.identify(contextKey: contextKey)
                note("identify(\(contextKey))")
            }
        }
        .buttonStyle(.bordered)
        .font(.footnote)
    }

    func note(_ message: String) {
        lastSignal = message
    }
}

struct ItemListView : View {
    @Environment(ViewModel.self) var viewModel: ViewModel

    var body: some View {
        List {
            ForEach(viewModel.items) { item in
                NavigationLink(value: item) {
                    Label {
                        Text(item.itemTitle)
                    } icon: {
                        if item.favorite {
                            Image(systemName: "star.fill")
                                .foregroundStyle(.yellow)
                        }
                    }
                }
            }
            .onDelete { offsets in
                viewModel.items.remove(atOffsets: offsets)
            }
            .onMove { fromOffsets, toOffset in
                viewModel.items.move(fromOffsets: fromOffsets, toOffset: toOffset)
            }
        }
        .navigationDestination(for: Item.self) { item in
            ItemView(item: item)
                .navigationTitle(item.itemTitle)
        }
        .toolbar {
            ToolbarItemGroup {
                Button {
                    withAnimation {
                        viewModel.items.insert(Item(), at: 0)
                    }
                } label: {
                    Label("Add", systemImage: "plus")
                }
            }
        }
    }
}

struct ItemView : View {
    @State var item: Item
    @Environment(ViewModel.self) var viewModel: ViewModel
    @Environment(\.dismiss) var dismiss

    var body: some View {
        Form {
            TextField("Title", text: $item.title)
                .textFieldStyle(.roundedBorder)
            Toggle("Favorite", isOn: $item.favorite)
            DatePicker("Date", selection: $item.date)
            Text("Notes").font(.title3)
            TextEditor(text: $item.notes)
                .border(Color.secondary, width: 1.0)
        }
        .navigationBarBackButtonHidden()
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") {
                    dismiss()
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    viewModel.save(item: item)
                    dismiss()
                }
                .disabled(!viewModel.isUpdated(item))
            }
        }
    }
}

struct SettingsView : View {
    @Binding var appearance: String
    @Binding var welcomeName: String

    var body: some View {
        Form {
            TextField("Name", text: $welcomeName)
            Picker("Appearance", selection: $appearance) {
                Text("System").tag("")
                Text("Light").tag("light")
                Text("Dark").tag("dark")
            }
            if let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
               let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String {
                Text("Version \(version) (\(buildNumber))")
            }
            HStack {
                PlatformHeartView()
                Text("Powered by [Skip](https://skip.dev)")
            }
        }
    }
}

/// A view that shows a blue heart on iOS and a green heart on Android.
struct PlatformHeartView : View {
    var body: some View {
        #if os(Android)
        ComposeView {
            HeartComposer()
        }
        #else
        Text(verbatim: "💙")
        #endif
    }
}

#if SKIP
/// Use a ContentComposer to integrate Compose content. This code will be transpiled to Kotlin.
struct HeartComposer : ContentComposer {
    @Composable func Compose(context: ComposeContext) {
        androidx.compose.material3.Text("💚", modifier: context.modifier)
    }
}
#endif
