package logging

import (
	"fmt"
	"sync/atomic"
)

// Logger is the interface used for logging internal to the observability plugin.
type Logger interface {
	Error(v ...interface{})
	Errorf(format string, v ...interface{})
	Infof(format string, v ...interface{})
}

//nolint:gochecknoglobals
var loggerInstance atomic.Pointer[Logger]

type noopLogger struct{}

func (d noopLogger) Error(_ ...interface{})            {}
func (d noopLogger) Errorf(_ string, _ ...interface{}) {}
func (d noopLogger) Infof(_ string, _ ...interface{})  {}

// ConsoleLogger is a logger that logs to the console.
// This is intended for use in debugging the plugin itself.
type ConsoleLogger struct{}

// Error logs a message to the console.
func (c ConsoleLogger) Error(v ...interface{}) {
	fmt.Printf("ERROR: %v\n", v...)
}

// Errorf logs a formatted message to the console.
func (c ConsoleLogger) Errorf(format string, v ...interface{}) {
	fmt.Printf("ERROR: %s\n", fmt.Sprintf(format, v...))
}

// Infof logs a formatted message to the console.
func (c ConsoleLogger) Infof(format string, v ...interface{}) {
	fmt.Printf("INFO: %s\n", fmt.Sprintf(format, v...))
}

// ClearLogger clears the logger returning to a no-op logger.
func ClearLogger() {
	loggerInstance.Store(nil)
}

// SetLogger sets the logger to the provided logger.
func SetLogger(l Logger) {
	loggerInstance.Store(&l)
}

// GetLogger returns the current logger instance.
func GetLogger() Logger {
	logger := loggerInstance.Load()
	if logger == nil {
		return noopLogger{}
	}
	return *logger
}
