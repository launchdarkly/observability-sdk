package context

import (
	gocontext "context"

	ldclient "github.com/launchdarkly/go-server-sdk/v7"
)

type contextKey string

const launchDarklyClientKey contextKey = "launchDarklyClient"

func WithLaunchDarklyClient(ctx gocontext.Context, client *ldclient.LDClient) gocontext.Context {
	return gocontext.WithValue(ctx, launchDarklyClientKey, client)
}

func LaunchDarklyClientFromContext(ctx gocontext.Context) *ldclient.LDClient {
	return ctx.Value(launchDarklyClientKey).(*ldclient.LDClient)
}
