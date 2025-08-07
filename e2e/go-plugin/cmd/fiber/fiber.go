package main

import (
	"fmt"
	"log"
	"os"
	"time"

	"github.com/gofiber/contrib/otelfiber/v2"
	"github.com/gofiber/fiber/v2"

	"github.com/launchdarkly/go-sdk-common/v3/ldcontext"
	ld "github.com/launchdarkly/go-server-sdk/v7"
	"github.com/launchdarkly/go-server-sdk/v7/ldplugins"
	ldobserve "github.com/launchdarkly/observability-sdk/go"

	"dice/internal/version"
)

func main() {
	if err := run(); err != nil {
		log.Fatalln(err)
	}
}

func run() (err error) {
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

	app := fiber.New()
	// This connects fiber to otel.
	app.Use(otelfiber.Middleware())

	app.Get("/ping", func(c *fiber.Ctx) error {
		pling, _ := client.BoolVariationCtx(c.UserContext(), "pling", ldcontext.New("bob"), false)
		if pling {
			return c.SendString("pling")
		} else {
			return c.SendString("ping")
		}
	})

	err = app.Listen(":8080")
	if err != nil {
		fmt.Println("Error starting server", err)
		return err
	}

	return nil
}
