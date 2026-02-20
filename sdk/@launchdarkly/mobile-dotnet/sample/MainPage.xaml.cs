using LaunchDarkly.SessionReplay;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client;

namespace MauiSample9;

public partial class MainPage : ContentPage
{
	private readonly HttpClient _httpClient = new();

	public MainPage()
	{
		InitializeComponent();
	}

	// --- Masking Navigation ---

	private async void OnMenuSelectionChanged(object? sender, SelectionChangedEventArgs e)
	{
		if (e.CurrentSelection == null || e.CurrentSelection.Count == 0)
			return;

		var selected = e.CurrentSelection[0] as string;
		if (string.IsNullOrEmpty(selected))
			return;

		switch (selected)
		{
			case "Credit Card":
				await Shell.Current.GoToAsync(nameof(CreditCardPage));
				break;
			case "Number Pad":
				await Shell.Current.GoToAsync(nameof(NumberPadPage));
				break;
		}

		if (sender is CollectionView cv)
			cv.SelectedItem = null;
	}

	// --- Session Replay ---

	private void OnSessionReplayToggled(object? sender, ToggledEventArgs e)
	{
		var ldNative = MauiProgram.LdNative;
		if (ldNative is not null)
			ldNative.Replay.IsEnabled = e.Value;

		Console.WriteLine($"Session Replay toggled: {e.Value}");
	}

	// --- Identify ---

	private void OnIdentifyUserClicked(object? sender, EventArgs e)
	{
		var userContext = Context.Builder("single-userkey")
			.Name("Bob Bobberson")
			.Build();

		LdClient.Instance.Identify(userContext, TimeSpan.FromSeconds(5));
		Console.WriteLine("Identified as User");
	}

	private void OnIdentifyMultiClicked(object? sender, EventArgs e)
	{
		var userContext = Context.Builder("multi-username")
			.Name("multi-username")
			.Build();
		var deviceContext = Context.Builder(ContextKind.Of("device"), "iphone")
			.Name("iphone")
			.Build();

		var multiContext = Context.MultiBuilder()
			.Add(userContext)
			.Add(deviceContext)
			.Build();

		LdClient.Instance.Identify(multiContext, TimeSpan.FromSeconds(5));
		Console.WriteLine("Identified as Multi");
	}

	private void OnIdentifyAnonClicked(object? sender, EventArgs e)
	{
		var anonContext = Context.Builder("anonymous-userkey")
			.Anonymous(true)
			.Build();

		LdClient.Instance.Identify(anonContext, TimeSpan.FromSeconds(5));
		Console.WriteLine("Identified as Anonymous");
	}

	// --- Instrumentation ---

	private async void OnTriggerHttpRequestClicked(object? sender, EventArgs e)
	{
		try
		{
			var response = await _httpClient.GetAsync("https://www.google.com");
			Console.WriteLine($"HTTP Response: {response.StatusCode}");
		}
		catch (Exception ex)
		{
			Console.WriteLine($"HTTP Request failed: {ex.Message}");
		}
	}

	private void OnTriggerCrashClicked(object? sender, EventArgs e)
	{
		throw new InvalidOperationException("Failed to connect to bogus server.");
	}

	// --- Metrics ---

	private void OnMetricClicked(object? sender, EventArgs e)
	{
		LDObserve.RecordMetric("test-gauge", 50.0);
		Console.WriteLine("Metric (gauge) triggered");
	}

	private void OnHistogramClicked(object? sender, EventArgs e)
	{
		LDObserve.RecordHistogram("test-histogram", 15.0);
		Console.WriteLine("Histogram triggered");
	}

	private void OnCountClicked(object? sender, EventArgs e)
	{
		LDObserve.RecordCount("test-counter", 10.0);
		Console.WriteLine("Count triggered");
	}

	private void OnIncrementalClicked(object? sender, EventArgs e)
	{
		LDObserve.RecordIncr("test-incremental-counter", 12.0);
		Console.WriteLine("Incremental triggered");
	}

	private void OnUpDownCounterClicked(object? sender, EventArgs e)
	{
		LDObserve.RecordUpDownCounter("test-up-down-counter", 25.0);
		Console.WriteLine("UpDownCounter triggered");
	}

	// --- Customer API ---

	private void OnTriggerErrorClicked(object? sender, EventArgs e)
	{
		LDObserve.RecordError("Manual error womp womp", "The error that caused the other error.");
		Console.WriteLine("Error triggered");
	}

	private void OnTriggerLogClicked(object? sender, EventArgs e)
	{
		LDObserve.RecordLog(
			"Test Log",
			LDObserve.Severity.Info,
			new Dictionary<string, object?> { { "FakeAttribute", "FakeVal" } }
		);
		Console.WriteLine("Log triggered");
	}

	private void OnSendCustomLogClicked(object? sender, EventArgs e)
	{
		var message = CustomLogEntry.Text;
		if (!string.IsNullOrEmpty(message))
		{
			LDObserve.RecordLog(message, LDObserve.Severity.Info);
			Console.WriteLine($"Custom log sent: {message}");
		}
	}

	private void OnTriggerNestedSpansClicked(object? sender, EventArgs e)
	{
		// TODO: LDObserve.StartSpan when API is available
		Console.WriteLine("Nested Spans triggered – API not yet available");
	}

	private void OnSendCustomSpanClicked(object? sender, EventArgs e)
	{
		var spanName = CustomSpanEntry.Text;
		if (!string.IsNullOrEmpty(spanName))
		{
			// TODO: LDObserve.StartSpan when API is available
			Console.WriteLine($"Custom span sent: {spanName} – API not yet available");
		}
	}

	private void OnEvaluateFlagClicked(object? sender, EventArgs e)
	{
		var flagKey = FlagKeyEntry.Text;
		if (string.IsNullOrEmpty(flagKey))
		{
			DisplayAlert("Flag", "Flag key cannot be empty", "OK");
			return;
		}

		var result = LdClient.Instance.BoolVariation(flagKey, false);
		DisplayAlert("Flag", $"{flagKey}: {result}", "OK");
		Console.WriteLine($"Flag {flagKey}: {result}");
	}
}
