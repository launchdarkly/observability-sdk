using Microsoft.OpenApi.Models;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Server;

// DOTNET Setup
var builder = WebApplication.CreateBuilder(args);
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "Sandbox API", Description = "This is a sandbox API for dotnet SDK development", Version = "v1" });
});

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
string SdkKey = Environment.GetEnvironmentVariable("LAUNCHDARKLY_SDK_KEY");

if (string.IsNullOrEmpty(SdkKey))
{
    Console.WriteLine("*** Please set LAUNCHDARKLY_SDK_KEY environment variable to your LaunchDarkly SDK key first\n");
    Environment.Exit(1);
}

var ldConfig = Configuration.Default(SdkKey);

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

// API Endpoints
app.MapGet("/", () => "Hello World!");
app.MapGet("/featureFlag", () => {
    var featureFlag = client.BoolVariation("sample-flag", context, false);
    return Results.Ok(featureFlag);
});

app.Run();
