using Microsoft.OpenApi.Models;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Server;
using Microsoft.AspNetCore.Mvc;
using System.Globalization;
using System.Diagnostics;
using OpenTelemetry.Exporter;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using LaunchDarkly.Sdk.Server.Telemetry;

// DOTNET Setup
var builder = WebApplication.CreateBuilder(args);

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "Sandbox API", Description = "This is a sandbox API for dotnet SDK development", Version = "v1" });
});

// OpenTelemetry Setup
const string serviceName = "sandbox-api";
// activitySource for custom tracing
var activitySource = new ActivitySource("sandbox-api");

builder.Logging.AddOpenTelemetry(options =>
{
    options
        .SetResourceBuilder(
            ResourceBuilder.CreateDefault()
                .AddService(serviceName))
        .AddConsoleExporter()
        .AddOtlpExporter(otlpOptions =>
        {
            otlpOptions.Endpoint = new Uri("http://localhost:4318/v1/logs");
            otlpOptions.Protocol = OtlpExportProtocol.HttpProtobuf;
        });
});
builder.Services.AddOpenTelemetry()
      .ConfigureResource(resource => resource.AddService(serviceName))
      .WithTracing(tracing => tracing
          .AddAspNetCoreInstrumentation()
          .AddSource("sandbox-api")
          .AddConsoleExporter()
          .AddOtlpExporter(otlpOptions =>
          {
              otlpOptions.Endpoint = new Uri("http://localhost:4318/v1/traces");
              otlpOptions.Protocol = OtlpExportProtocol.HttpProtobuf;
          }))
      .WithMetrics(metrics => metrics
          .AddAspNetCoreInstrumentation()
          .AddConsoleExporter()
          .AddOtlpExporter(otlpOptions =>
          {
              otlpOptions.Endpoint = new Uri("http://localhost:4318/v1/metrics");
              otlpOptions.Protocol = OtlpExportProtocol.HttpProtobuf;
          }));

var app = builder.Build();

// Swagger Setup
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI(c =>
    {
        c.SwaggerEndpoint("/swagger/v1/swagger.json", "Sandbox API V1");
    });
}

// LaunchDarkly SDK Setup
string? SdkKey = Environment.GetEnvironmentVariable("LAUNCHDARKLY_SDK_KEY");

if (string.IsNullOrEmpty(SdkKey))
{
    Console.WriteLine("*** Please set LAUNCHDARKLY_SDK_KEY environment variable to your LaunchDarkly SDK key first\n");
    Environment.Exit(1);
}

var ldConfig = Configuration.Builder(SdkKey)
    .Hooks(Components.Hooks()
        .Add(TracingHook.Default())
    ).Build();

var client = new LdClient(ldConfig);

if (client.Initialized)
{
    Console.WriteLine("*** SDK successfully initialized!\n");
}
else
{
    Console.WriteLine("*** SDK failed to initialize\n");
    Environment.Exit(1);
}

var context = Context.Builder("context-key-123abc")
    .Name("Robert Pakko")
    .Build();

// Business Logic
string HandleRollDice([FromServices]ILogger<Program> logger, string? player)
{
    var useD20 = client.BoolVariation("sample-flag", context, false);

    var result = useD20
        ? Random.Shared.Next(1, 21) // Roll a D20 if flag is true
        : Random.Shared.Next(1, 7); // Roll a D6 if flag is false

    // OpenTelemetry Tracing
    using var activity = activitySource.StartActivity("roll-dice");
        
    if(activity != null)
    {
        // Add attributes to the span
        activity.SetTag("player", player ?? "anonymous");
        activity.SetTag("feature.sample-flag", useD20);
        activity.SetTag("dice.type", useD20 ? "d20" : "d6");
        activity.SetTag("dice.result", result);
    }

    // Logging result
    if (string.IsNullOrEmpty(player))
    {
        logger.LogInformation("Anonymous player is rolling the dice: {result}", result);
    }
    else
    {
        logger.LogInformation("{player} is rolling the dice: {result}", player, result);
    }

    return result.ToString(CultureInfo.InvariantCulture);
}

// API Endpoints
app.MapGet("/", () => "Hello World!");
app.MapGet("/featureFlag", () => {
    var featureFlag = client.BoolVariation("sample-flag", context, false);
    return Results.Ok(featureFlag);
});
app.MapGet("/rolldice/{player?}", HandleRollDice);

app.Run();
