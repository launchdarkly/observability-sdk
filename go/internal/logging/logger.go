package logging

type Logger interface {
	Error(v ...interface{})
	Errorf(format string, v ...interface{})
}

// log is this packages logger
var Log struct {
	Logger
}

// noop default logger
type noopLogger struct{}

func (d noopLogger) Error(_ ...interface{})            {}
func (d noopLogger) Errorf(_ string, _ ...interface{}) {}

func ClearLogger() {
	Log.Logger = noopLogger{}
}

func SetLogger(l Logger) {
	Log.Logger = l
}
