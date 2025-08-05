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

## Examples

This project contains several examples, each demonstrating the LaunchDarkly Observability plugin with different Go frameworks and libraries:

- [Fiber Example](#1-fiber-example-cmdfiber)
- [Gin Example](#2-gin-example-cmdgin)
- [Gorilla Mux Example](#3-gorilla-mux-example-cmdgorillamux)
- [Standard HTTP Example](#4-standard-http-example-cmdhttp)
- [Logrus Example](#5-logrus-example-cmdlogrus)

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
 