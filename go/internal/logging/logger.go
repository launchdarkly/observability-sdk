package logging

import "fmt"

// Logger is the interface used for logging internal to the observability plugin.
type Logger interface {
	Error(v ...interface{})
	Errorf(format string, v ...interface{})
}

// Log is the global logger instance.
//
//nolint:gochecknoglobals
var Log struct {
	Logger
}

type noopLogger struct{}

func (d noopLogger) Error(_ ...interface{})            {}
func (d noopLogger) Errorf(_ string, _ ...interface{}) {}

// ConsoleLogger is a logger that logs to the console.
// This is intended for use in debugging the plugin itself.
type ConsoleLogger struct{}

// Error logs a message to the console.
func (c ConsoleLogger) Error(v ...interface{}) {
	fmt.Println(v...)
}

// Errorf logs a formatted message to the console.
func (c ConsoleLogger) Errorf(format string, v ...interface{}) {
	fmt.Println(fmt.Sprintf(format, v...))
}

// ClearLogger clears the logger returning to a no-op logger.
func ClearLogger() {
	Log.Logger = noopLogger{}
}

// SetLogger sets the logger to the provided logger.
func SetLogger(l Logger) {
	Log.Logger = l
}
