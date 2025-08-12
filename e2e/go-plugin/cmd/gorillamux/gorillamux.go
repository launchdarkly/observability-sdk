package main

import (
	"context"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"time"

	"github.com/gorilla/mux"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gorilla/mux/otelmux"

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

	client, _ := ld.MakeCustomClient(os.Getenv("LAUNCHDARKLY_SDK_KEY"),
		ld.Config{
			Plugins: []ldplugins.Plugin{
				ldobserve.NewObservabilityPlugin(
					ldobserve.WithEnvironment("test"),
					ldobserve.WithServiceName("go-plugin-example"),
					ldobserve.WithServiceVersion(version.Commit),
				),
			},
		}, 5*time.Second)

	ctx = appcontext.WithLaunchDarklyClient(ctx, client)

	router := mux.NewRouter()

	router.Use(otelmux.Middleware("gorillamux"))
	router.HandleFunc("/rolldice", dice.Rolldice)

	// Start HTTP server.
	srv := &http.Server{
		Addr:         ":8080",
		BaseContext:  func(_ net.Listener) context.Context { return ctx },
		ReadTimeout:  time.Second,
		WriteTimeout: 10 * time.Second,
		Handler:      router,
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
