using System.Collections.Generic;
using LaunchDarkly.Logging;

namespace LaunchDarkly.Observability.Test
{
    /// <summary>
    /// Test log adapter that captures log messages for testing purposes
    /// </summary>
    public class TestLogAdapter : ILogAdapter
    {
        private readonly List<LogMessage> _messages = new List<LogMessage>();

        public class LogMessage
        {
            public LogLevel Level { get; set; }
            public string Name { get; set; }
            public string Message { get; set; }
        }

        public IReadOnlyList<LogMessage> Messages => _messages.AsReadOnly();

        private void Log(LogLevel level, object message, string loggerName)
        {
            _messages.Add(new LogMessage
            {
                Level = level,
                Name = loggerName,
                Message = message?.ToString()
            });
        }

        public IChannel NewChannel(string name)
        {
            return new TestChannel(this, name);
        }

        public void Clear()
        {
            _messages.Clear();
        }

        private class TestChannel : IChannel
        {
            private readonly TestLogAdapter _adapter;
            private readonly string _name;

            public TestChannel(TestLogAdapter adapter, string name)
            {
                _adapter = adapter;
                _name = name;
            }

            public bool IsEnabled(LogLevel level)
            {
                return true; // Always capture log messages for testing
            }

            public void Log(LogLevel level, object message)
            {
                _adapter.Log(level, message, _name);
            }

            public void Log(LogLevel level, string format, object param)
            {
                var message = string.Format(format, param);
                _adapter.Log(level, message, _name);
            }

            public void Log(LogLevel level, string format, object param1, object param2)
            {
                var message = string.Format(format, param1, param2);
                _adapter.Log(level, message, _name);
            }

            public void Log(LogLevel level, string format, params object[] allParams)
            {
                var message = string.Format(format, allParams);
                _adapter.Log(level, message, _name);
            }
        }
    }
}
