# Mobile Telemetry Specification (Android & iOS)

## Overview
This document defines the required spans, events, and attributes to be captured by mobile observability SDKs for Android and iOS. The goal is to provide consistent, actionable telemetry for mobile applications, following OpenTelemetry conventions and best practices, and drawing inspiration from leading mobile OTel libraries (e.g., Embrace, OpenTelemetry SIGs).

## Table of Contents
- [General Principles](#general-principles)
- [Span Types](#span-types)
  - [App Lifecycle Spans](#app-lifecycle-spans)
  - [Screen/View Lifecycle Spans](#screenview-lifecycle-spans)
  - [User Interaction Spans](#user-interaction-spans)
  - [Network Request Spans](#network-request-spans)
  - [Background Task Spans](#background-task-spans)
  - [Error/Crash Spans](#errorcrash-spans)
- [Events](#events)
- [Attributes](#attributes)
- [Resource Attributes](#resource-attributes)
- [References](#references)

---

## General Principles
- **Lightweight**: Minimize performance and battery impact.
- **Semantic**: Use OpenTelemetry semantic conventions where possible.
- **Actionable**: Capture data that enables debugging, performance analysis, and user experience improvement.
- **Privacy**: Avoid capturing PII or sensitive data by default.

---

## Span Types

### App Lifecycle Spans
| Span Name         | When to Start           | When to End             | Example Attributes           |
|------------------|------------------------|-------------------------|-----------------------------|
| `app.start`      | App process starts     | First screen shown      | `os.version`  |
| `app.background` | App goes to background | App returns foreground  | `reason`, `duration`        |
| `app.terminate`  | App termination begins | Process exit            | `reason`                    |

#### Example Attributes
- `os.version`, `device.model`, `device.manufacturer`
- `app.version`, `app.build_number`

### Screen/View Lifecycle Spans
#### Span Naming Convention
Screen lifecycle span names **must** be in the format `<Verb> <screen name>`, where:
- `<Verb>` is a platform-specific lifecycle event (see below)
- `<screen name>` is the logical name of the screen or view

**Android Verbs:** `Paused`, `Resumed`, `Started`, `Stopped`, `Created`, `Destroyed`

**iOS Verbs:** `Foreground Active`, `Background`, `Unattached`, `WillAppear`, `DidAppear`, `WillDisappear`, `DidDisappear`

**Example Span Names:**
- `Paused HomeScreen` (Android)
- `Resumed LoginScreen` (Android)
- `Foreground Active MainView` (iOS)
- `Unattached SettingsView` (iOS)

| Span Name Format      | When to Start                | When to End                  | Example Attributes         |
|----------------------|-----------------------------|------------------------------|---------------------------|
| `<Verb> <screen name>`| On lifecycle event          | On next lifecycle event      | `screen.name` |

#### Attributes
- `screen.name`: Logical name of the screen/view
- `screen.class`: Class or identifier
- `screen.orientation`: Device orientation during the screen's lifecycle (e.g., "portrait", "landscape")

### User Interaction Spans
| Span Name             | When to Start                | When to End                  | Example Attributes         |
|----------------------|-----------------------------|------------------------------|---------------------------|
| `user.tap`           | User taps UI element        | Action completes             | `target.id`, `screen.name`, `target.type`|
| `user.swipe`         | User swipes                 | Swipe completes              | `direction`, `screen.name`, `target.id` |
| `user.long_press`    | User long-presses element   | Long press completes         | `target.id`, `screen.name`, `target.type` |
| `user.scroll`        | User starts scrolling       | Scroll ends                  | `direction`, `screen.name`, `target.id` |
| `user.drag`          | User starts drag            | Drag ends                    | `target.id`, `screen.name`, |

#### Attributes
- `target.id`, `target.type` Target element for drag-and-drop for interaction
- `screen.name`
- `direction`: `up`, `down`, `left`, `right` (for swipe/scroll)
- `position.x`, `position.y`: Absolute pixel coordinates of the interaction relative to the top-left of the screen

### Network Request Spans
| Span Name             | When to Start                | When to End                  | Example Attributes         |
|----------------------|-----------------------------|------------------------------|---------------------------|
| `http.request`       | Request initiated           | Response received/error      | See below                 |

#### Example Attributes (per [OTel HTTP SemConv](https://opentelemetry.io/docs/specs/semconv/attributes-http/))
- `http.method`, `http.url`, `http.status_code`
- `http.request_content_length`, `http.response_content_length`
- `network.connection.type`: `wifi`, `cellular`, etc.
- `network.carrier.name`, `network.carrier.mcc`, `network.carrier.mnc`
- `http.request.header.keys`, `http.response.header.keys` (only header keys, not values, for security)

| Attribute Key                    | Example Value         | Notes                                    |
|----------------------------------|----------------------|------------------------------------------|
| `http.method`                   | "GET"               |                                          |
| `http.url`                      | "https://api.com/x"  |                                          |
| `http.status_code`              | 200                  |                                          |
| `http.request_content_length`    | 1234                 |                                          |
| `http.response_content_length`   | 5678                 |                                          |
| `network.connection.type`        | "wifi"              |                                          |
| `network.carrier.name`           | "Verizon"           |                                          |
| `network.carrier.mcc`            | "310"               |                                          |
| `network.carrier.mnc`            | "260"               |                                          |
| `http.request.header.keys`       | ["Authorization", "Accept"] | Only header keys, not values, for security |
| `http.response.header.keys`      | ["Content-Type", "X-RateLimit-Reset"] | Only header keys, not values, for security |

> **Note:** For security and privacy, only header keys (not values) should be included in span attributes.

### Background Task Spans
| Span Name             | When to Start                | When to End                  | Example Attributes         |
|----------------------|-----------------------------|------------------------------|---------------------------|
| `background.task`    | Task scheduled/started      | Task completes/fails         | `task.name`, `duration`   |

#### Example Attributes
- `task.name`, `task.type`
- `trigger`: `push_notification`, `background_fetch`, etc.

### Error/Crash Spans
| Span Name             | When to Start                | When to End                  | Example Attributes         |
|----------------------|-----------------------------|------------------------------|---------------------------|
| `error`              | Exception thrown             | Exception handled            | `exception.type`, `exception.message`, `exception.stacktrace` |
| `crash`              | App crash detected           | App restarts                 | `reason`, `signal`|

#### Example Attributes
- `exception.type`, `exception.message`, `exception.stacktrace`

---

## Events
Events are time-stamped annotations on spans. Common events to capture:
- `network.change`: Network connectivity changes (type, strength)
- `memory.warning`: OS memory warning
- `anr`: Application Not Responding (Android)
- `low_power_mode`: Device enters/exits low power
- `push.received`: Push notification received
- `push.opened`: Push notification opened

---

## Resource Attributes

The following attributes MUST be set as resource attributes at SDK initialization, as they are stable for the process lifetime (see [OpenTelemetry Resource Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/resource/)):

| Attribute Key              | Example Value         | Source/Convention         |
|----------------------------|----------------------|--------------------------|
| `service.name`             | "my-mobile-app"      | OTel core                |
| `service.version`          | "1.2.3"              | OTel core                |
| `service.instance.id`      | "uuid-1234"          | OTel core                |
| `device.model.name`        | "iPhone13,4"         | OTel device              |
| `device.manufacturer`      | "Apple"              | OTel device              |
| `os.type`                  | "iOS"                | OTel OS                  |
| `os.version`               | "17.4.1"             | OTel OS                  |
| `app.name`                 | "MyApp"              | OTel app                 |
| `app.version`              | "1.2.3"              | OTel app                 |
| `app.build.number`         | "123"                | OTel app                 |
| `telemetry.sdk.language`   | "swift"              | OTel SDK                 |
| `telemetry.sdk.name`       | "opentelemetry"      | OTel SDK                 |
| `telemetry.sdk.version`    | "1.0.0"              | OTel SDK                 |

These attributes should NOT be set on individual spans, but will be attached to all telemetry exported from the process.

---

## Attributes
Use OpenTelemetry semantic conventions where possible. Key attributes to include as **span attributes** (dynamic, per-operation):

### Network
| Attribute Key                | Example Value         |
|------------------------------|----------------------|
| `network.connection.type`    | "wifi"              |
| `network.carrier.name`       | "Verizon"           |
| `network.carrier.mcc`        | "310"               |
| `network.carrier.mnc`        | "260"               |

### Screen/View
| Attribute Key                | Example Value         |
|------------------------------|----------------------|
| `screen.name`                | "HomeScreen"        |
| `screen.class`               | "HomeViewController"|

### User Interaction
| Attribute Key                | Example Value         |
|------------------------------|----------------------|
| `input.type`                 | "text"              |

### Error/Crash
| Attribute Key                | Example Value         |
|------------------------------|----------------------|
| `exception.type`             | "NullPointerException"|
| `exception.message`          | "Object was null"   |
| `exception.stacktrace`       | (string)             |

---

## References
- [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [OpenTelemetry Android Resource Attributes](https://opentelemetry.io/docs/specs/semconv/resource/android/)
- [Embrace Mobile OTel Blog](https://embrace.io/blog/solving-android-app-issues-with-opentelemetry/)
- [OpenTelemetry Client-Side Telemetry SIG](https://github.com/open-telemetry/community/blob/main/sigs/client-side-telemetry/README.md)
- [OpenTelemetry iOS Resource Attributes](https://opentelemetry.io/docs/specs/semconv/resource/ios/)

---

## Rationale
This spec is designed to:
- Enable end-to-end tracing of user journeys and app performance
- Provide actionable context for debugging and optimization
- Align with OpenTelemetry and industry best practices for mobile observability

> **Note:** This spec should be reviewed and updated as OpenTelemetry mobile conventions evolve and as new use cases arise. 