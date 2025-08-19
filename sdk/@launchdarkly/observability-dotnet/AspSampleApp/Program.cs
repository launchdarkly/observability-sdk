using System.ComponentModel;
using System.Diagnostics;
using LaunchDarkly.Observability;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Server;
using LaunchDarkly.Sdk.Server.Integrations;
using Microsoft.Extensions.Logging;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var config = Configuration.Builder(Environment.GetEnvironmentVariable("LAUNCHDARKLY_SDK_KEY"))
    .Plugins(new PluginConfigurationBuilder()
        .Add(ObservabilityPlugin.Builder(builder.Services)
            .WithServiceName("ryan-test-service")
            .WithServiceVersion("0.0.0")
            .WithOtlpEndpoint("http://localhost:4318")
            .Build())).Build();

// Building the LdClient with the Observability plugin. This line will add services to the web application.
var client = new LdClient(config);

// Client must be built before this line.
var app = builder.Build();

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

var summaries = new[]
{
    "Freezing", "Bracing", "Chilly", "Cool", "Mild", "Warm", "Balmy", "Hot", "Sweltering", "Scorching"
};

app.MapGet("/recordexception", () =>
    {
        Observe.RecordException(new InvalidOperationException("this is a recorded exception"),
            new Dictionary<string, object>
            {
                { "key", "value" },
            });
        return "";
    })
    .WithName("GetRecordException")
    .WithOpenApi();

app.MapGet("/recordmetrics", () =>
    {
        var random = new Random();

        // Record a gauge metric (CPU usage percentage)
        var cpuUsage = Math.Round(random.NextDouble() * 100, 2);
        Observe.RecordMetric("cpu_usage_percent", cpuUsage, new Dictionary<string, object>
        {
            { "environment", "development" },
            { "service", "asp-sample" }
        });

        // Record a counter with random value (requests processed)
        var requestsProcessed = random.Next(1, 100);
        Observe.RecordCount("requests_processed", requestsProcessed, new Dictionary<string, object>
        {
            { "operation", "test" },
            { "status", "success" }
        });

        // Record an increment (counter with value 1)
        Observe.RecordIncr("endpoint_hits", new Dictionary<string, object>
        {
            { "endpoint", "/recordmetrics" },
            { "method", "GET" }
        });

        // Record a histogram value (request duration in seconds)
        var requestDuration = Math.Round(random.NextDouble() * 2.0, 3); // 0-2 seconds
        Observe.RecordHistogram("request_duration_seconds", requestDuration, new Dictionary<string, object>
        {
            { "handler", "recordmetrics" },
            { "response_code", "200" }
        });

        // Record an up-down counter (active connections - positive)
        var connectionDelta = random.Next(1, 10);
        Observe.RecordUpDownCounter("active_connections", connectionDelta, new Dictionary<string, object>
        {
            { "connection_type", "http" },
            { "region", "us-east-1" }
        });

        // Record another up-down counter with negative delta (queue items processed)
        var queueDelta = -random.Next(1, 5);
        Observe.RecordUpDownCounter("queue_size", queueDelta, new Dictionary<string, object>
        {
            { "queue_name", "processing" },
            { "priority", "high" }
        });

        return new
        {
            message = "Metrics recorded successfully",
            metrics = new
            {
                gauge = $"cpu_usage_percent: {cpuUsage}%",
                counter = $"requests_processed: +{requestsProcessed}",
                increment = "endpoint_hits: +1",
                histogram = $"request_duration_seconds: {requestDuration}s",
                upDownCounter1 = $"active_connections: +{connectionDelta}",
                upDownCounter2 = $"queue_size: {queueDelta}"
            }
        };
    })
    .WithName("GetRecordMetrics")
    .WithOpenApi();

app.MapGet("/weatherforecast", () =>
    {
        var isMercury =
            client.BoolVariation("isMercury", Context.New(ContextKind.Of("request"), Guid.NewGuid().ToString()));
        var forecast = Enumerable.Range(1, 5).Select(index =>
                new WeatherForecast
                (
                    DateOnly.FromDateTime(DateTime.Now.AddDays(index)),
                    Random.Shared.Next(isMercury ? -170 : -20, isMercury ? 400 : 55),
                    summaries[Random.Shared.Next(summaries.Length)]
                ))
            .ToArray();
        return forecast;
    })
    .WithName("GetWeatherForecast")
    .WithOpenApi();

app.MapGet("/recordlog", () =>
    {
        var random = new Random();
        var logMessages = new[]
        {
            "User authentication successful",
            "Database connection established", 
            "Cache miss occurred, falling back to database",
            "API rate limit approaching threshold",
            "Background job completed successfully"
        };
        
        var severityLevels = new[] { LogLevel.Information, LogLevel.Warning, LogLevel.Error, LogLevel.Debug };
        
        var message = logMessages[random.Next(logMessages.Length)];
        var severity = severityLevels[random.Next(severityLevels.Length)];
        
        Observe.RecordLog(message, severity, new Dictionary<string, object>
        {
            {"component", "asp-sample-app"},
            {"endpoint", "/recordlog"},
            {"timestamp", DateTime.UtcNow.ToString("O")},
            {"user_id", Guid.NewGuid().ToString()}
        });
        
        return new
        {
            message = "Log recorded successfully",
            log_entry = new
            {
                message,
                severity = severity.ToString(),
                component = "asp-sample-app"
            }
        };
    })
    .WithName("GetRecordLog")
    .WithOpenApi();

app.MapGet("/manualinstrumentation", () =>
    {
        using (Observe.StartActivity("manual-instrumentation", ActivityKind.Internal,
                   new Dictionary<string, object> { { "test", "attribute" } }))
        {
            var enableMetrics = client.BoolVariation("enableMetrics",
                Context.New(ContextKind.Of("request"), Guid.NewGuid().ToString()));

            if (!enableMetrics) return "Manual instrumentation completed";
            Observe.RecordIncr("manual_instrumentation_calls");
            return "Manual instrumentation completed with metrics enabled";
        }
    })
    .WithName("GetManualInstrumentation")
    .WithOpenApi();

app.Run();

record WeatherForecast(DateOnly Date, int TemperatureC, string? Summary)
{
    public int TemperatureF => 32 + (int)(TemperatureC / 0.5556);
}
