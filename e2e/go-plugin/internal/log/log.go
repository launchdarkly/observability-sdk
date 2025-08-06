package log

import (
	"io"
	"log"
	"net/http"

	"github.com/sirupsen/logrus"
	"go.opentelemetry.io/otel"
)

var (
	tracer = otel.Tracer("logger")
)

type address struct {
	City   string
	State  string
	Zip    string
	Street string
}

type person struct {
	Name    string
	Address address
}

// DoLog is a handler that logs a message.
func DoLog(w http.ResponseWriter, r *http.Request) {
	ctx, span := tracer.Start(r.Context(), "DoLog")
	defer span.End()

	var intVal = 123
	var int64Val int64 = 12356
	var floatVal = 123.456
	var boolVal = true
	var arrayVal = []string{"a", "b", "c"}
	var mapVal = map[string]string{"a": "b", "c": "d"}

	// Including the context in the logrus fields will allow the log to be
	// correlated with the surrounding trace/span.
	logrus.WithContext(ctx).WithFields(logrus.Fields{
		"example": "value",
		"int":     intVal,
		"int64":   int64Val,
		"float":   floatVal,
		"bool":    boolVal,
		"array":   arrayVal,
		"map":     mapVal,
		"user": person{
			Name: "John Doe",
			Address: address{
				City:   "New York",
				State:  "NY",
				Zip:    "10001",
				Street: "123 Main St",
			},
		},
	}).Warn("logged")

	if _, err := io.WriteString(w, "logged"); err != nil {
		log.Printf("Write failed: %v\n", err)
	}
}
