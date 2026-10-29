using System;
using System.Collections.Generic;
using LaunchDarkly.Observability;

namespace MauiSample9;

/// <summary>
/// Repro #1: global attributes (e.g. UTM params) on all telemetry.
/// Set once at startup before the SDK starts; the existing span/log buttons on
/// <see cref="MainPage"/> print what to look for on each exported row.
/// </summary>
public static class GlobalAttributesRepro
{
	public static readonly Dictionary<string, object?> GlobalAttributes = new()
	{
		["utm_campaign"] = "spring_promo",
		["utm_source"] = "email",
	};

	public const string ExpectGlobal = "[#1] expect utm_campaign=spring_promo on this row";
	public const string ExpectPerCallWins = "[#1] expect utm_source=per_call (per-call wins over global)";

	/// <summary>
	/// Must run synchronously before <c>LDObserve.Init</c> to exercise the set-before-init buffer.
	/// </summary>
	public static void ApplyGlobalAttributes()
	{
		LDObserve.SetAttributes(GlobalAttributes);
		Console.WriteLine("[#1] global attributes set before init: utm_campaign=spring_promo, utm_source=email");
	}
}
