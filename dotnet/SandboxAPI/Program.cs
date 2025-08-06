using Microsoft.OpenApi.Models;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Server;
using Microsoft.AspNetCore.Mvc;
using System.Globalization;
using LaunchDarkly.Sdk.Server.Telemetry;
using OpenTelemetry.Exporter;
using System.Diagnostics;

// DOTNET Setup
var builder = WebApplication.CreateBuilder(args);
builder.Services.AddEndpointsApiExplorer();

// Swagger Setup
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "Sandbox API", Description = "This is a sandbox API for dotnet SDK development", Version = "v1" });
});

// Create custom sampling configuration
var samplingConfig = new SamplingConfig
{
    Spans = new List<SpanSamplingConfig>
    {
        new SpanSamplingConfig
        {
            Name = new MatchConfig { MatchValue = "roll-dice" },
            SamplingRatio = 2 // Sample 1 in 2 spans with name "roll-dice"
        },
        new SpanSamplingConfig
        {
            Attributes = new List<AttributeMatchConfig>
            {
                new AttributeMatchConfig
                {
                    Key = new MatchConfig { MatchValue = "dice.type" },
                    Attribute = new MatchConfig { MatchValue = "d20" }
                }
            },
            SamplingRatio = 1 // Always sample spans with dice.type = "d20"
        }
    },
    Logs = new List<LogSamplingConfig>
    {
        new LogSamplingConfig
        {
            Message = new MatchConfig { RegexValue = ".*rolling.*" },
            SamplingRatio = 3 // Sample 1 in 3 log messages containing "rolling"
        }
    }
};

// Create custom sampler
var customSampler = ObservabilityExtensions.CreateDefaultSampler(samplingConfig);

var observabilityConfig = new ObservabilityPluginConfig
{
    ProjectId = "abc-123",
    ServiceName = "sandbox-api",
    OtlpEndpoint = "http://localhost:4318",
    OtlpProtocol = OtlpExportProtocol.HttpProtobuf,
    Sampler = customSampler // Add the custom sampler
};

// Add observability (OpenTelemetry) configuration
builder.AddObservability(observabilityConfig);

var app = builder.Build();

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
    .Plugins(Components.Plugins()
        .Add(new ObservabilityPlugin(observabilityConfig)))
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
    using var activity = new Activity("roll-dice");
    activity.Start();
        
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
