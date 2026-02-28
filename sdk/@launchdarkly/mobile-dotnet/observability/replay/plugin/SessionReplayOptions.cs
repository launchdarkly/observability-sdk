using System;

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

        public PrivacyOptions()
        {
        }

        public PrivacyOptions(
            bool maskTextInputs = true,
            bool maskWebViews = false,
            bool maskLabels = false,
            bool maskImages = false,
            double minimumAlpha = 0.02)
        {
            MaskTextInputs = maskTextInputs;
            MaskWebViews = maskWebViews;
            MaskLabels = maskLabels;
            MaskImages = maskImages;
            MinimumAlpha = minimumAlpha;
        }
    }

    public bool IsEnabled { get; set; } = true;
    public string ServiceName { get; set; } = "sessionreplay-dotnet";
    public PrivacyOptions Privacy { get; set; } = new PrivacyOptions();

    public SessionReplayOptions()
    {
    }

    public SessionReplayOptions(
        bool isEnabled = true,
        string serviceName = "sessionreplay-dotnet",
        PrivacyOptions? privacy = null
    )
    {
        IsEnabled = isEnabled;
        ServiceName = serviceName;
        Privacy = privacy ?? new PrivacyOptions();
    }
}
