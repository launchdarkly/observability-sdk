package context

import (
	gocontext "context"

	ldclient "github.com/launchdarkly/go-server-sdk/v7"
)

type contextKey string

const launchDarklyClientKey contextKey = "launchDarklyClient"

// WithLaunchDarklyClient adds the LaunchDarkly client to the context.
func WithLaunchDarklyClient(ctx gocontext.Context, client *ldclient.LDClient) gocontext.Context {
	return gocontext.WithValue(ctx, launchDarklyClientKey, client)
}

// LaunchDarklyClientFromContext returns the LaunchDarkly client from the context.
// If the client is not found, it returns nil.
func LaunchDarklyClientFromContext(ctx gocontext.Context) *ldclient.LDClient {
	value := ctx.Value(launchDarklyClientKey)
	ldc, _ := value.(*ldclient.LDClient)
	return ldc
}
