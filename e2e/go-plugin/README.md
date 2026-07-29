# LaunchDarkly Observability Plugin Examples

This project demonstrates how to use the LaunchDarkly Observability plugin with various Go libraries and frameworks.

## Prerequisites

- Go 1.24.3 or later
- A LaunchDarkly SDK key

## Setup

1. Clone this repository:
```bash
git clone https://github.com/launchdarkly/observability-sdk.git
cd observability-sdk/e2e/go-plugin
```

2. Set your LaunchDarkly SDK key as an environment variable:
```bash
export LAUNCHDARKLY_SDK_KEY="your-sdk-key-here"
```

## Pointing somewhere other than LaunchDarkly

The examples export to LaunchDarkly by default. The HTTP example also honours the
standard endpoint variable, so it can be pointed at a local stack instead — OTLP
over HTTP, port 4318:

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
```

Data is attributed to the project the SDK key belongs to, so use a key from the
environment the data should land in.

To see what the SDK sends without a stack at all, run the `otlpdump` command
(below) and point the example at that.

## Examples

This project contains several examples, each demonstrating the LaunchDarkly Observability plugin with different Go frameworks and libraries:

- [Fiber Example](#1-fiber-example-cmdfiber)
- [Gin Example](#2-gin-example-cmdgin)
- [Gorilla Mux Example](#3-gorilla-mux-example-cmdgorillamux)
- [Standard HTTP Example](#4-standard-http-example-cmdhttp)
- [Logrus Example](#5-logrus-example-cmdlogrus)
- [Pre-Initialize Example](#6-pre-initialize-example-cmdpreinit)
- [OTLP Dump](#otlp-dump-cmdotlpdump)

### 1. Fiber Example (`cmd/fiber/`)

A simple web server using the [Fiber](https://gofiber.io/) framework with OpenTelemetry instrumentation.

**Features:**
- Uses Fiber web framework
- Demonstrates basic feature flag evaluation
- Shows OpenTelemetry integration with Fiber

**To run:**
```bash
go run cmd/fiber/fiber.go
```

**Endpoints:**
- `GET /ping` - Returns "ping" or "pling" based on the `pling` feature flag

### 2. Gin Example (`cmd/gin/`)

A web server using the [Gin](https://gin-gonic.com/) framework with OpenTelemetry instrumentation.

**Features:**
- Uses Gin web framework
- Demonstrates feature flag evaluation in HTTP handlers
- Shows OpenTelemetry integration with Gin

**To run:**
```bash
go run cmd/gin/gin.go
```

**Endpoints:**
- `GET /ping` - Returns a JSON response with "pong" or "pling" based on the `pling` feature flag

### 3. Gorilla Mux Example (`cmd/gorillamux/`)

A web server using the [Gorilla Mux](https://github.com/gorilla/mux) router with OpenTelemetry instrumentation.

**Features:**
- Uses Gorilla Mux router
- Demonstrates graceful shutdown handling
- Shows OpenTelemetry integration with Gorilla Mux
- Includes a dice rolling endpoint with feature flag integration

**To run:**
```bash
go run cmd/gorillamux/gorillamux.go
```

**Endpoints:**
- `GET /rolldice` - Rolls a dice and returns the result, with verbose output controlled by the `verbose-response` feature flag

### 4. Standard HTTP Example (`cmd/http/`)

A web server using Go's standard `net/http` package with OpenTelemetry instrumentation.

**Features:**
- Uses standard `net/http` package
- Demonstrates manual OpenTelemetry span creation
- Shows feature flag evaluation with custom spans
- Includes graceful shutdown handling

**To run:**
```bash
go run cmd/http/http.go
```

**Endpoints:**
- `GET /rolldice` - Rolls a dice and returns the result, with verbose output controlled by the `verbose-response` feature flag
- `GET /error/plain` - Records an error with no stack of its own, so the SDK has to capture one. The reported frames should start at the handler's helper, not inside the SDK
- `GET /error/stack` - Records a `pkg/errors` error, whose stack was captured where the error was created, three calls below the handler
- `GET /error/wrapped` - Records a wrapped error. The frames should reach the root cause, not stop at the wrap
- `GET /error/goroutine` - Records an error raised in nested closures on another goroutine, with no span to attach it to

### 5. Logrus Example (`cmd/logrus/`)

A web server demonstrating structured logging with [Logrus](https://github.com/sirupsen/logrus) and OpenTelemetry.

**Features:**
- Uses Logrus for structured logging
- Demonstrates OpenTelemetry integration with Logrus
- Shows how to log structured data with various types
- Includes feature flag evaluation

**To run:**
```bash
go run cmd/logrus/logrus.go
```

**Endpoints:**
- `GET /log` - Logs structured data with various field types and demonstrates Logrus + OpenTelemetry integration

### 6. Pre-Initialize Example (`cmd/preinit/`)

A web server demonstrating advanced observability initialization patterns, including pre-initializing the observability plugin before the LaunchDarkly client.

**Features:**
- Uses `ldobserve.PreInitialize()` to initialize observability before the LaunchDarkly client
- Demonstrates using observability without the LaunchDarkly client (with limited features)
- Shows the `NewObservabilityPluginWithoutInit()` pattern for pre-initialized observability
- Uses standard `net/http` package with OpenTelemetry instrumentation
- Includes graceful shutdown handling
- Demonstrates manual span creation and attribute setting

**To run:**
```bash
go run cmd/preinit/preinit.go
```

**Endpoints:**
- `GET /rolldice` - Rolls a dice and returns the result, with verbose output controlled by the `verbose-response` feature flag

**Use Cases:**
- Advanced initialization scenarios where you need observability before the LaunchDarkly client
- Using observability features without a LaunchDarkly client (some features will be unavailable)
- Custom initialization timing requirements
 
### OTLP Dump (`cmd/otlpdump/`)

An OTLP endpoint that prints the exceptions it receives, including the source
context attached to every frame. It stands in for the backend while working on
how errors are recorded: what reaches it is what the backend would have parsed,
so a change can be checked without a stack running and without waiting for the
data to appear in the UI.

**To run:**

```bash
go run ./cmd/otlpdump
```

Then, in another shell, point an example at it and record some errors:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 go run ./cmd/http
curl localhost:8080/error/wrapped
```

Each exception prints as its frames, innermost first, with the reported line
marked:

```
===== exception on span "/error/wrapped" =====
exception.type = *errors.withStack
exception.message = could not start worker: worker config has no timeout
--- 12 frames ---
[0] failure.connectWorker in .../internal/failure/failure.go at line 65
       | func connectWorker() (int, error) {
       | 	timeout, ok := workerSettings["timeout"]
       | 	if !ok {
    >> | 		return 0, errors.New("worker config has no timeout")
       | 	}
       | 	return timeout, nil
       | }
```

A frame printed as `(no source for this frame on this host)` is the normal result
for a binary running without its source tree, a container built from `scratch`
being the obvious case. Pass `-text` to print the plain text stack trace too,
which is what the backend has to fall back on when no frames are sent.
