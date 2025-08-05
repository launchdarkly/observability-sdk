package main

import (
	"net/http"
	"os"

	"time"

	"github.com/gin-gonic/gin"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"

	"github.com/launchdarkly/go-sdk-common/v3/ldcontext"
	ld "github.com/launchdarkly/go-server-sdk/v7"
	"github.com/launchdarkly/go-server-sdk/v7/ldplugins"

	"dice/internal/version"

	ldobserve "github.com/launchdarkly/observability-sdk/go"
)

func main() {
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
	r := gin.Default()
	// This connects gin to otel.
	r.Use(otelgin.Middleware("gin"))

	r.GET("/ping", func(c *gin.Context) {
		pling, _ := client.BoolVariationCtx(c.Request.Context(), "pling", ldcontext.New("bob"), false)
		if pling {
			c.JSON(http.StatusOK, gin.H{
				"message": "pling",
			})
		} else {
			c.JSON(http.StatusOK, gin.H{
				"message": "pong",
			})
		}
	})
	r.Run() // listen and serve on 0.0.0.0:8080 (for windows "localhost:8080")
}
