using System;

namespace LaunchDarkly.Observability
{
    /// <summary>
    /// Helper methods for working with environment variables.
    /// </summary>
    internal static class EnvironmentHelper
    {
        /// <summary>
        /// Returns the provided value if it's not null or empty, otherwise attempts to get the value from the specified environment variable.
        /// If the environment variable is also not set, returns the default value.
        /// </summary>
        /// <param name="value">The primary value to use.</param>
        /// <param name="environmentVariable">The name of the environment variable to check if the primary value is null or empty.</param>
        /// <param name="defaultValue">The default value to use if both the primary value and environment variable are not set.</param>
        /// <returns>The resolved value based on the precedence: primary value > environment variable > default value.</returns>
        public static string GetValueOrEnvironment(string value, string environmentVariable, string defaultValue = "")
        {
            if (!string.IsNullOrWhiteSpace(value))
            {
                return value;
            }

            var envValue = Environment.GetEnvironmentVariable(environmentVariable);
            return !string.IsNullOrWhiteSpace(envValue) ? envValue : defaultValue;
        }
    }
}
