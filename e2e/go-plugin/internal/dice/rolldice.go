package dice

import (
	"fmt"
	"io"
	"log"
	"math/rand"
	"net/http"

	"go.opentelemetry.io/otel"

	"github.com/launchdarkly/go-sdk-common/v3/ldcontext"

	appcontext "dice/internal/context"
)

var (
	tracer = otel.Tracer("rolldice")
)

// Rolldice is a handler that rolls a die and returns the result.
func Rolldice(w http.ResponseWriter, r *http.Request) {
	ctx, span := tracer.Start(r.Context(), "roll")
	defer span.End()
	//nolint:gosec // Used for rolling a die, does not need to be cryptographically secure.
	roll := 1 + rand.Intn(6)

	client := appcontext.LaunchDarklyClientFromContext(ctx)

	if client == nil {
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
		return
	}

	// This flag evaluation will be represented in the span.
	verbose, _ := client.BoolVariationCtx(ctx, "verbose-response", ldcontext.New("bob"), false)

	var resp string
	if verbose {
		resp = fmt.Sprintf("You got a %d\n", roll)
	} else {
		resp = fmt.Sprintf("%d\n", roll)
	}

	if _, err := io.WriteString(w, resp); err != nil {
		log.Printf("Write failed: %v\n", err)
	}
}
