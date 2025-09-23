package main

import (
	"context"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"time"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"

	ld "github.com/launchdarkly/go-server-sdk/v7"
	"github.com/launchdarkly/go-server-sdk/v7/ldplugins"
	ldobserve "github.com/launchdarkly/observability-sdk/go"

	appcontext "dice/internal/context"
	"dice/internal/dice"
	"dice/internal/version"
)

func main() {
	if err := run(); err != nil {
		log.Fatalln(err)
	}
}

func run() (err error) {
	// Handle SIGINT (CTRL+C) gracefully.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	// Initialize the observability plugin ahead of the LaunchDarkly client.
	// This is generally only required for advanced use cases and is not the
	// recommended way to initialize observability.
	// Observability can also be used without the LaunchDarkly client at all
	// by using this method. Some features will not be available when using
	// observability without the LaunchDarkly client.
	ldobserve.PreInitialize(os.Getenv("LAUNCHDARKLY_SDK_KEY"),
		ldobserve.WithEnvironment("test"),
		ldobserve.WithServiceName("go-plugin-example"),
		ldobserve.WithServiceVersion(version.Commit),
	)

	client, _ := ld.MakeCustomClient(os.Getenv("LAUNCHDARKLY_SDK_KEY"),
		ld.Config{
			Plugins: []ldplugins.Plugin{
				// This special form of constructing the observability plugin
				// is used when observability has been pre-initialized.
				ldobserve.NewObservabilityPluginWithoutInit(),
			},
		}, 5*time.Second)

	ctx = appcontext.WithLaunchDarklyClient(ctx, client)

	// Start HTTP server.
	srv := &http.Server{
		Addr:         ":8080",
		BaseContext:  func(_ net.Listener) context.Context { return ctx },
		ReadTimeout:  time.Second,
		WriteTimeout: 10 * time.Second,
		Handler:      newHTTPHandler(),
	}
	srvErr := make(chan error, 1)
	go func() {
		srvErr <- srv.ListenAndServe()
	}()

	// Wait for interruption.
	select {
	case err = <-srvErr:
		// Error when starting HTTP server.
		return err
	case <-ctx.Done():
		stop()
	}

	ldobserve.Shutdown()

	// When Shutdown is called, ListenAndServe immediately returns ErrServerClosed.
	err = srv.Shutdown(context.Background())
	return err
}

func newHTTPHandler() http.Handler {
	mux := http.NewServeMux()

	_, span := ldobserve.StartSpan(context.Background(), "test-span", []trace.SpanStartOption{})
	span.SetAttributes(attribute.String("test-attribute", "test-value"))
	span.End()

	// handleFunc is a replacement for mux.HandleFunc
	// which enriches the handler's HTTP instrumentation with the pattern as the http.route.
	handleFunc := func(pattern string, handlerFunc func(http.ResponseWriter, *http.Request)) {
		// Configure the "http.route" for the HTTP instrumentation.
		handler := otelhttp.WithRouteTag(pattern, http.HandlerFunc(handlerFunc))
		mux.Handle(pattern, handler)
	}

	// Register handlers.Als
	handleFunc("/rolldice", dice.Rolldice)

	// Add HTTP instrumentation for the whole server.
	handler := otelhttp.NewHandler(
		mux,
		"/",
		otelhttp.WithSpanNameFormatter(func(operation string, r *http.Request) string {
			// Return the route as the span name
			return r.URL.Path
		}))
	return handler
}
