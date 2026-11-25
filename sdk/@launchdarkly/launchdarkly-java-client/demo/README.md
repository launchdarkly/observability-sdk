# LaunchDarkly Java Client Demo App

A sample app for the LaunchDarkly client-side Java SDK, the
SDK for desktop and other single-user Java apps. It holds these demos, built from
one project:

- **The dashboard**, a Swing window whose progress meters, line chart and bar
  chart are each behind a flag. Flip one in LaunchDarkly and the component
  updates in place.
- **The console demo**, which prints a few flags on a timer with their
  variation and evaluation reason, and reports each change the moment the SDK
  delivers it. It's the better tool for checking SDK behavior.
- **The session replay demos**, one small window per desktop toolkit the
  observability plugin records: JavaFX, SWT, Apache Pivot and Compose for
  Desktop. The dashboard is the Swing one: in live mode it records too.

The SDK isn't released yet, so this app builds against a local copy of it. The
observability plugin is built from source in [`../plugin`](../plugin).

## Quick Start

Install the SDK into your local Maven repository, then run the demo. The
observability plugin needs no install: the build includes `../plugin`. No
LaunchDarkly credentials are needed:

```bash
(cd path/to/java-client-sdk && ./gradlew :launchdarkly-java-client-sdk:publishToMavenLocal)
./gradlew run
```

With no credential set, the app drives the flags itself through the SDK's
`TestData` source, moving to the next sample values every 5 seconds; each change
prints as a `>>` line. Press Ctrl-C to stop, or type
`q`. `./gradlew run --args="--help"` lists every command and option.

This is not a shortcut around the real code path. A `TestData` update goes
through the SDK's flag store and fires the same flag change listeners a
streaming update from LaunchDarkly would.

## The Dashboard

```bash
./gradlew runDashboard
```

A setup dialog asks for a mobile key or client-side ID (pre-filled from `.env`)
and the user to evaluate flags for. Leave the credential blank to run offline:
a panel of switches on the right then stands in for the LaunchDarkly UI.

| Flag | Controls |
| --- | --- |
| `dashboard` | The keystone: a prerequisite of the other three |
| `dashboard-progress-meters` | The three progress meters |
| `dashboard-line-chart` | The line chart |
| `dashboard-bar-chart` | The bar chart |

All four are booleans in the `launchdarkly-java-client-side-sdk-demo-app`
project, available to both mobile keys and client-side IDs. A component that is
off drains to zero rather than disappearing, so the layout stays put. Turn
`dashboard` off and all three go with it. LaunchDarkly evaluates the
prerequisite and the client only receives the result, so the app never checks
`dashboard` itself. Offline, the switches apply the same rule by hand.

The header shows the connection state and the current user, with a **Switch
user** button that calls `identify`. Use it with an individual target or rule
to show different users getting different dashboards. Each change also prints a
`>>` line to the terminal.

See [DashboardWindow.java](src/main/java/com/launchdarkly/example/clientdemo/dashboard/DashboardWindow.java)
for the listener wiring.

## Session Replay Demos

In live mode the observability plugin also records the app's windows for
session replay, so a session can be watched in LaunchDarkly next to its flags, errors and logs.
The dashboard records itself. Each other desktop toolkit has a small demo, the
same form in each:

```bash
./gradlew runJavaFxDemo
./gradlew runSwtDemo
./gradlew runPivotDemo
./gradlew runComposeDemo
```

Replay needs LaunchDarkly, so set `LD_MOBILE_KEY` or `LD_CLIENT_SIDE_ID` in
`.env`. Without one, a demo shows its window and says it is not recording. Each
prints its session id; find the session under **Sessions** in Observability,
where it can take several minutes to appear. LaunchDarkly leaves out sessions with
no interaction, so move the mouse, type and click **Save** before closing the
window. **Save** sends a `replay-demo-save` custom event, which shows on the
replay's timeline next to the click.

Replay records only the active window, and masks it in the app before anything
is sent. With the default privacy profile, as on Android, the email field and the
password are grey in the recording, and the flag list shows. The note under the
form is masked by hand, with each toolkit's own mark:

| Demo | Masks the note with |
| --- | --- |
| JavaFX | the `ld-mask` style class (`LDReplay.MASK_STYLE_CLASS`) |
| SWT | `setData(LDReplay.MASK_PROPERTY, true)` |
| Apache Pivot | `getUserData().put(LDReplay.MASK_PROPERTY, true)` |
| Compose for Desktop | `Modifier.ldMask()`, from the plugin's Compose library |

The **Save** button is named `save`, so its clicks are reported as `save`: by
its id in JavaFX, its data under `"name"` in SWT, its name in Pivot, and
`Modifier.ldId("save")` in Compose.

Set `REPLAY_DEBUG=true` to log each batch of frames the plugin sends. On macOS
SWT runs only on the main thread, which the SWT demo's run task asks for.
[ReplayDemo.java](src/main/java/com/launchdarkly/example/clientdemo/replay/ReplayDemo.java)
holds the setup they share.

## Sharing It

The SDK isn't published, so someone else can't build this from source. Give
them the runnable jar instead, which has both demos and every dependency,
including the SDK, inside:

```bash
./gradlew fatJar
# build/libs/launchdarkly-java-client-demo-app-all.jar
```

They need Java 21 or later, and then:

```bash
java -jar launchdarkly-java-client-demo-app-all.jar            # the dashboard
java -jar launchdarkly-java-client-demo-app-all.jar console    # the console demo
java -jar launchdarkly-java-client-demo-app-all.jar doctor     # diagnostics
```

No credential is bundled. They can type one into the dashboard's dialog, put a
`.env` in the directory they run from, or set `LD_MOBILE_KEY` /
`LD_CLIENT_SIDE_ID` in the environment. With none, both demos run offline.

Docker isn't a good fit for the dashboard, since a container has no display.

## Live Mode

Put a mobile key or a client-side ID in `.env` and the app streams from
LaunchDarkly instead. Gradle reads `.env` itself, so there is nothing to export:

```bash
cp .env.example .env    # then fill in LD_MOBILE_KEY
./gradlew run
```

`.env` is gitignored, so a real key stays out of the repository. Set one
credential, not both:

| Credential | Variable | Sees |
| --- | --- | --- |
| Mobile key | `LD_MOBILE_KEY` | Every flag in the environment |
| Client-side ID | `LD_CLIENT_SIDE_ID` | Only flags with "SDKs using Client-side ID" turned on |

By default the console watches the dashboard's four flags (see
[The Dashboard](#the-dashboard)) for the user `demo-user@example.com`, so both
demos show the same thing. Point it at other flags with `LD_FLAG_KEYS`. Change one in the LaunchDarkly UI and
a `>>` line appears within a second or two.

## What To Look For

```
--- flags | STREAMING ---
   dashboard                  true         variation 0  FALLTHROUGH
   dashboard-progress-meters  true         variation 0  FALLTHROUGH
   dashboard-line-chart       true         variation 0  FALLTHROUGH
   dashboard-bar-chart        true         variation 0  FALLTHROUGH
>> dashboard-line-chart changed: false        variation 1  OFF
```

- **The snapshot at startup** shows every watched flag's value, its variation
  index and the evaluation reason, with the connection mode in the header. Type
  `l` for a fresh one at any time.
- **`>>` lines** come from the SDK's flag change listeners and connection status
  listener, as soon as the SDK delivers the change. After startup, they are the
  only output.
- **`--ticks`** adds a snapshot every `--interval` seconds, if you want a
  heartbeat. It's off by default.
- **The context** is a `user` context with an `env` attribute, so targeting
  rules can vary values by environment. `i KEY` switches to another user. The SDK also adds
  `ld_application` and `ld_device` contexts, which the banner shows. Turn that
  off with `LD_AUTO_ENV_ATTRIBUTES=false`.

## The CLI

Two commands, and every setting configurable by option as well as by
environment variable. A command-line option shadows the environment variable of
the same name, which shadows the built-in default.

```bash
./gradlew run                    # the demo, reading .env automatically
./gradlew doctor                 # diagnostics, then exit
./gradlew run --args="--help"
./gradlew run --args="--interval 1 --flags new-checkout,banner-text"
```

Installed as a script, it is less noisy than going through Gradle:

```bash
./gradlew installDist
build/install/launchdarkly-java-client-demo-app/bin/launchdarkly-java-client-demo-app doctor
```

### While running

| Command | Does |
| --- | --- |
| `l` | Print the watched flags now |
| `a` | Print every flag the client has |
| `i KEY` | Identify as another context, with the same kind and `env` |
| `t EVENT` | Track a custom event |
| `o` | Go offline, or back online |
| `f` | Flush analytics events |
| `s` | Print connection status |
| `h` | List these commands |
| `q` | Quit cleanly |

In offline mode, the first flag is always off for the context key `vip`, so
`i vip` shows targeting at work.

Pass `--no-interactive` to skip reading the terminal. The demo also degrades
quietly on its own when stdin isn't a terminal.

### `doctor`

Run `./gradlew doctor` when something isn't working. It prints the SDK version,
the connection state and each watched flag's value, JSON type and evaluation
reason, then exits with status 1 if any check failed:

```
 dashboard
   value             true (BOOLEAN)
   variation         0
   reason            FALLTHROUGH
   status            OK
```

A flag the client can't see shows `NOT FOUND`, with a note on the likely cause.

### Stopping

`q`, Ctrl-C and `SIGTERM` all shut down the same way: the client closes, which
sends any pending analytics events, and a summary prints.

## Configuration

See `.env.example`. Everything is optional; a credential selects live mode.

| Variable | Default | Description |
| --- | --- | --- |
| `LD_MOBILE_KEY` | none | Mobile key |
| `LD_CLIENT_SIDE_ID` | none | Client-side ID, instead of a mobile key |
| `LD_FLAG_KEYS` | the four dashboard flags | Flags to watch, comma-separated |
| `LD_CONTEXT_KIND` | `user` | Kind of the context |
| `LD_CONTEXT_KEY` | `demo-user@example.com` | Key of the context |
| `LD_ENV` | `development` | Set as the `env` attribute, for targeting rules |
| `LD_AUTO_ENV_ATTRIBUTES` | `true` | Add the `ld_application` and `ld_device` contexts |
| `DEMO_TICKS` | `false` | Reprint the watched flags every interval |
| `DEMO_INTERVAL_SECONDS` | `5` | Seconds between ticks, and between sample changes offline |
| `LD_USER_EMAIL` | `demo-user@example.com` | Dashboard only: pre-fills the user's email, the context key |
| `LD_USER_NAME` | `Demo User` | Dashboard only: pre-fills the user's name |

The console settings each have a command-line equivalent: `--mobile-key`,
`--client-side-id`, `--flags`, `--context-kind`, `--context-key`, `--env`,
`--auto-env`, `--ticks` and `--interval`. Both demos also read a `.env` from the working
directory when run with `java -jar`.

For a quick non-interactive check, pipe the quit command in:

```bash
printf 'q\n' | ./gradlew run --args="--interval 1"
```

## How It's Wired

See [DemoApp.java](src/main/java/com/launchdarkly/example/clientdemo/DemoApp.java).

1. **One client, created with `LDClient.init(config, context, 5)`.** It waits up
   to 5 seconds for flag values, then carries on and keeps trying in the
   background. The client-side SDK evaluates for one context at a time;
   `identify` switches it.
2. **SDK logging goes to SLF4J** through `logAdapter(LDSLF4J.adapter())`, so it
   shares Logback's console output. The SDK logs under `LaunchDarklySdk`; set
   that logger to `DEBUG` in [logback.xml](src/main/resources/logback.xml) to see
   every request.
3. **Evaluation reasons are on** (`evaluationReasons(true)`), so the demo can
   show why each flag has its value.
4. **Flag values are cached** in `~/.launchdarkly/java-client-sdk`, so a restart
   has values at once, even before it reaches LaunchDarkly. Offline mode turns
   the cache off.
5. **LaunchDarkly Observability is on in live mode**, through the
   [observability plugin](../plugin).
   Flag evaluations, identifies, `t` events and launches show up in the
   environment's Observability views, as service `launchdarkly-java-client-demo-app`
   or `launchdarkly-java-client-demo-dashboard`. The dashboard also reports its
   windows, clicks, and when it goes to the background. Offline mode sends nothing,
   since its credential is a placeholder.

## Developing Against a Local SDK Checkout

By default the SDK resolves from `mavenLocal`, which is what a consumer of the
published artifact would get. While changing the SDK and the demo at once, build
the SDK from source instead:

```bash
./gradlew run -PsdkDir=path/to/java-client-sdk
```

That makes the checkout an included build, so SDK edits show up here with no
republishing step. The plugin in `../plugin` is always an included build, so its
edits show up here too.

## Troubleshooting

Run `doctor` first.

| Symptom | Cause |
| --- | --- |
| `Could not find com.launchdarkly:launchdarkly-java-client-sdk:0.1.0` | Install the SDK with `publishToMavenLocal` (see Quick Start), or use `-PsdkDir` |
| `initialized false` and no values in live mode | Wrong credential, or this machine can't reach LaunchDarkly |
| A flag shows `NOT FOUND` | Wrong key or environment; with a client-side ID, the flag also needs "SDKs using Client-side ID" turned on |
| `Set LD_MOBILE_KEY or LD_CLIENT_SIDE_ID, not both.` | Both credentials are set; remove one |

## CI

`.github/workflows/java-client-observability.yml`, at the root of this
repository, builds the demos against the plugin from source and runs the
offline smoke tests (`doctor`, then a run that quits) on every pull request and
push to `main` that touches this folder.

## Requirements

- Java 21 or later (the SDK itself supports Java 8 and later)
- A local install of the SDK, or a checkout of it passed as `-PsdkDir`

## License

Apache 2.0, as for the rest of this repository. See [LICENSE](../../../../LICENSE).

Written by Brad Bunce.
