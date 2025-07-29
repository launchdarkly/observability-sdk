package logging

import (
	"fmt"
	"sync/atomic"
)

// Logger is the interface used for logging internal to the observability plugin.
type Logger interface {
	Error(v ...interface{})
	Errorf(format string, v ...interface{})
}

//nolint:gochecknoglobals
var loggerInstance *atomic.Value = defaultLoggerInstance()

func defaultLoggerInstance() *atomic.Value {
	v := &atomic.Value{}
	v.Store(noopLogger{})
	return v
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
	loggerInstance.Store(noopLogger{})
}

// SetLogger sets the logger to the provided logger.
func SetLogger(l Logger) {
	loggerInstance.Store(l)
}

// GetLogger returns the current logger instance.
func GetLogger() Logger {
	return loggerInstance.Load().(Logger)
}
