// Package failure records the error shapes the SDK has to describe, so that the
// frames and source context reaching the UI can be checked against a running
// application rather than only a unit test.
package failure

import (
	"context"
	"fmt"
	"io"
	"log"
	"net/http"
	"sync"

	"github.com/pkg/errors"

	ldobserve "github.com/launchdarkly/observability-sdk/go"
)

// workerSettings stands in for configuration loaded at startup. The missing
// timeout is what every route here trips over, so the source lines reported for
// the innermost frame are the same few lines of real code each time.
var workerSettings = map[string]int{"retries": 3}

// Plain records an error that carries no stack of its own, which is what
// fmt.Errorf and the standard errors package produce. The SDK has to capture a
// stack while recording, so the innermost frame should be readTimeout below
// rather than the SDK's own recording code.
func Plain(w http.ResponseWriter, r *http.Request) {
	if _, err := readTimeout(); err != nil {
		ldobserve.RecordError(r.Context(), err)
		respond(w, "recorded an error with no stack of its own")
		return
	}
	respond(w, "worker config was complete, nothing to record")
}

func readTimeout() (int, error) {
	timeout, ok := workerSettings["timeout"]
	if !ok {
		return 0, fmt.Errorf("worker config has no timeout")
	}
	return timeout, nil
}

// WithStack records an error from pkg/errors, which captures a stack when the
// error is created. The SDK reports that stack instead of the one it could
// capture here, so the frames should start at connectWorker, three calls below
// this handler.
func WithStack(w http.ResponseWriter, r *http.Request) {
	if _, err := startWorker(); err != nil {
		ldobserve.RecordError(r.Context(), err)
		respond(w, "recorded an error carrying its own stack")
		return
	}
	respond(w, "worker started, nothing to record")
}

func startWorker() (int, error) {
	return connectWorker()
}

func connectWorker() (int, error) {
	timeout, ok := workerSettings["timeout"]
	if !ok {
		return 0, errors.New("worker config has no timeout")
	}
	return timeout, nil
}

// Wrapped records a wrapped error, Go's answer to a chained exception: one error
// for the root cause and another for the context around it. Both messages belong
// in the text stack trace, and the frames come from where the cause was created.
func Wrapped(w http.ResponseWriter, r *http.Request) {
	if err := runWorker(); err != nil {
		ldobserve.RecordError(r.Context(), err)
		respond(w, "recorded a wrapped error")
		return
	}
	respond(w, "worker ran, nothing to record")
}

func runWorker() error {
	if _, err := connectWorker(); err != nil {
		return errors.Wrap(err, "could not start worker")
	}
	return nil
}

// InGoroutine records an error from nested closures on another goroutine, the
// shape whose frames used to arrive named after bare ordinals ("2") or with a
// trailing "in goroutine N". Nothing carries the request span onto that
// goroutine, so the SDK also has to start a span of its own here.
func InGoroutine(w http.ResponseWriter, r *http.Request) {
	var workers sync.WaitGroup
	workers.Add(1)
	go func() {
		defer workers.Done()
		func() {
			if _, err := connectWorker(); err != nil {
				ldobserve.RecordError(context.Background(), errors.Wrap(err, "worker goroutine failed"))
			}
		}()
	}()
	workers.Wait()

	respond(w, "recorded an error raised on another goroutine")
}

func respond(w http.ResponseWriter, message string) {
	if _, err := io.WriteString(w, message+"\n"); err != nil {
		log.Printf("Write failed: %v\n", err)
	}
}
