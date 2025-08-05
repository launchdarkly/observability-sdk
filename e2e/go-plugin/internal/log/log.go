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

type Address struct {
	City   string
	State  string
	Zip    string
	Street string
}

type Person struct {
	Name    string
	Address Address
}

func DoLog(w http.ResponseWriter, r *http.Request) {
	ctx, span := tracer.Start(r.Context(), "DoLog")
	defer span.End()

	var intVal int = 123
	var int64Val int64 = 12356
	var floatVal float64 = 123.456
	var boolVal bool = true
	var arrayVal []string = []string{"a", "b", "c"}
	var mapVal map[string]string = map[string]string{"a": "b", "c": "d"}

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
		"user": Person{
			Name: "John Doe",
			Address: Address{
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
