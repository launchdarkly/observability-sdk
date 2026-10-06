using System;
using System.Collections.Generic;

namespace LaunchDarkly.SessionReplay;

public class SessionReplayOptions
{
    public class PrivacyOptions
    {
        public bool MaskTextInputs { get; set; } = true;
        public bool MaskWebViews { get; set; } = false;
        public bool MaskLabels { get; set; } = false;
        public bool MaskImages { get; set; } = false;
        public double MinimumAlpha { get; set; } = 0.02;

        /// <summary>
        /// MAUI view types (e.g. <c>typeof(Label)</c> or your own <c>ContentView</c> subclass)
        /// to mask wherever they appear. Types match exactly; subclasses must be listed separately.
        /// </summary>
        public IList<Type> MaskViewTypes { get; set; } = new List<Type>();

        /// <summary>
        /// MAUI view types to reveal from global masking (e.g. <see cref="MaskLabels"/>).
        /// Types match exactly.
        /// </summary>
        public IList<Type> UnmaskViewTypes { get; set; } = new List<Type>();

        /// <summary>
        /// MAUI view types to leave out of recordings. On Android, where the native SDK has no
        /// per-view ignore, these are masked instead. Types match exactly.
        /// </summary>
        public IList<Type> IgnoreViewTypes { get; set; } = new List<Type>();

        public PrivacyOptions()
        {
        }

        public PrivacyOptions(
            bool maskTextInputs = true,
            bool maskWebViews = false,
            bool maskLabels = false,
            bool maskImages = false,
            double minimumAlpha = 0.02,
            IList<Type>? maskViewTypes = null,
            IList<Type>? unmaskViewTypes = null,
            IList<Type>? ignoreViewTypes = null)
        {
            MaskTextInputs = maskTextInputs;
            MaskWebViews = maskWebViews;
            MaskLabels = maskLabels;
            MaskImages = maskImages;
            MinimumAlpha = minimumAlpha;
            MaskViewTypes = maskViewTypes ?? new List<Type>();
            UnmaskViewTypes = unmaskViewTypes ?? new List<Type>();
            IgnoreViewTypes = ignoreViewTypes ?? new List<Type>();
        }
    }

    public bool IsEnabled { get; set; } = true;
    public string ServiceName { get; set; } = "sessionreplay-dotnet";
    public PrivacyOptions Privacy { get; set; } = new PrivacyOptions();

    /// <summary>
    /// Probability from <c>0.0</c> to <c>1.0</c> that session replay starts when enabled.
    /// Values at or below zero never record; values at or above one always record.
    /// </summary>
    public double SampleRate { get; set; } = 1.0;

    /// <summary>Target capture rate in frames per second.</summary>
    public double FrameRate { get; set; } = 1.0;

    /// <summary>
    /// Capture resolution: <c>1.0</c> = 1x (160 DPI), <c>2.0</c> = 2x, and so on.
    /// Higher values capture more detail at the cost of larger frames.
    /// </summary>
    public double Scale { get; set; } = 1.0;

    /// <summary>
    /// JPEG quality of exported frames, from <c>0.0</c> (smallest) to <c>1.0</c> (best).
    /// Values outside that range are clamped by the native SDK.
    /// </summary>
    public double ImageQuality { get; set; } = 0.3;

    public SessionReplayOptions()
    {
    }

    public SessionReplayOptions(
        bool isEnabled = true,
        string serviceName = "sessionreplay-dotnet",
        PrivacyOptions? privacy = null,
        double sampleRate = 1.0,
        double frameRate = 1.0,
        double scale = 1.0,
        double imageQuality = 0.3
    )
    {
        IsEnabled = isEnabled;
        ServiceName = serviceName;
        Privacy = privacy ?? new PrivacyOptions();
        SampleRate = sampleRate;
        FrameRate = frameRate;
        Scale = scale;
        ImageQuality = imageQuality;
    }
}
