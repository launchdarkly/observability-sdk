package otel

import (
	"encoding/json"
	"fmt"
	"os"
	"testing"
	"time"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/log"
	sdklog "go.opentelemetry.io/otel/sdk/log"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"

	"github.com/launchdarkly/observability-sdk/go/internal/gql"
	"go.opentelemetry.io/otel/sdk/log/logtest"
)

type inputEvent struct {
	Name       string         `json:"name"`
	Attributes map[string]any `json:"attributes"`
}

type inputSpan struct {
	Name       string         `json:"name"`
	Attributes map[string]any `json:"attributes"`
	Events     []inputEvent   `json:"events"`
}

type inputSamplingResult struct {
	Sample     bool                   `json:"sample"`
	Attributes map[string]interface{} `json:"attributes"`
}

type samplerFunctionCase struct {
	SamplerType    string              `json:"type"`
	ExpectedResult inputSamplingResult `json:"expected_result"`
}

type spanTestScenario struct {
	Description          string                                      `json:"description"`
	SamplingConfig       gql.GetSamplingConfigSamplingSamplingConfig `json:"samplingConfig"`
	InputSpan            inputSpan                                   `json:"inputSpan"`
	SamplerFunctionCases []samplerFunctionCase                       `json:"samplerFunctionCases"`
}

type inputLog struct {
	Message      string         `json:"message"`
	Attributes   map[string]any `json:"attributes"`
	SeverityText string         `json:"severityText"`
}

type logTestScenario struct {
	Description          string                                      `json:"description"`
	SamplingConfig       gql.GetSamplingConfigSamplingSamplingConfig `json:"samplingConfig"`
	InputLog             inputLog                                    `json:"inputLog"`
	SamplerFunctionCases []samplerFunctionCase                       `json:"samplerFunctionCases"`
}

type readonlySpanSubsetImpl struct {
	name       string
	attributes []attribute.KeyValue
	events     []sdktrace.Event
}

func (s *readonlySpanSubsetImpl) Name() string {
	return s.name
}

func (s *readonlySpanSubsetImpl) Attributes() []attribute.KeyValue {
	return s.attributes
}

func (s *readonlySpanSubsetImpl) Events() []sdktrace.Event {
	return s.events
}

func newSpanWorkAround(name string, events []sdktrace.Event, attributes []attribute.KeyValue) ReadonlySpanSubset {
	return &readonlySpanSubsetImpl{
		name:       name,
		attributes: attributes,
		events:     events,
	}
}

type spanTestScenarios []spanTestScenario

func toAttributes(in map[string]any) []attribute.KeyValue {
	attributes := make([]attribute.KeyValue, 0, len(in))
	for key, value := range in {
		attributes = append(attributes, toAttribute(key, value))
	}
	return attributes
}

func toAttribute(key string, value any) attribute.KeyValue {
	switch v := value.(type) {
	case string:
		return attribute.String(key, v)
	case int:
		return attribute.Int(key, v)
	case int64:
		return attribute.Int64(key, v)
	case float64:
		return attribute.Float64(key, v)
	case bool:
		return attribute.Bool(key, v)
	case []string:
		return attribute.StringSlice(key, v)
	case []int:
		return attribute.IntSlice(key, v)
	case []int64:
		return attribute.Int64Slice(key, v)
	case []float64:
		return attribute.Float64Slice(key, v)
	case []bool:
		return attribute.BoolSlice(key, v)
	default:
		// Fallback to string representation for unknown types
		panic(fmt.Sprintf("unknown type: %T", v))
	}
}

func toEvents(inputEvents []inputEvent) []sdktrace.Event {
	events := make([]sdktrace.Event, 0, len(inputEvents))
	for _, inputEvent := range inputEvents {
		event := sdktrace.Event{
			Name:       inputEvent.Name,
			Attributes: toAttributes(inputEvent.Attributes),
			Time:       time.Now(),
		}
		events = append(events, event)
	}
	return events
}

func toReadonlySpan(inputSpan inputSpan) ReadonlySpanSubset {
	attributes := toAttributes(inputSpan.Attributes)
	events := toEvents(inputSpan.Events)
	return newSpanWorkAround(inputSpan.Name, events, attributes)

}

// compareValues compares two values that might be different numeric types but represent the same value
func compareValues(expected, actual interface{}) bool {
	// If types are the same, use direct comparison
	if expected == actual {
		return true
	}

	// Handle numeric type comparisons
	switch exp := expected.(type) {
	case int:
		if act, ok := actual.(int); ok {
			return exp == act
		}
		if act, ok := actual.(int64); ok {
			return exp == int(act)
		}
		if act, ok := actual.(float64); ok {
			return float64(exp) == act
		}
	case float64:
		if act, ok := actual.(float64); ok {
			return exp == act
		}
		if act, ok := actual.(int); ok {
			return exp == float64(act)
		}
		if act, ok := actual.(int64); ok {
			return exp == float64(act)
		}
	case string:
		if act, ok := actual.(string); ok {
			return exp == act
		}
	case bool:
		if act, ok := actual.(bool); ok {
			return exp == act
		}
	}

	return false
}

func neverSampler(ratio int) bool {
	return false
}

func alwaysSampler(ratio int) bool {
	return true
}

func TestSpanScenarios(t *testing.T) {
	spanTestScenarios := spanTestScenarios{}
	spanTestScenariosJSON, err := os.ReadFile("span-test-scenarios.json")

	if err != nil {
		t.Fatalf("Failed to read span-test-scenarios.json: %v", err)
	}

	err = json.Unmarshal(spanTestScenariosJSON, &spanTestScenarios)
	if err != nil {
		t.Fatalf("Failed to unmarshal span-test-scenarios.json: %v", err)
	}

	for _, scenario := range spanTestScenarios {
		t.Run(scenario.Description, func(t *testing.T) {
			for _, samplerFunctionCase := range scenario.SamplerFunctionCases {
				var sampleFun SamplerFunc
				switch samplerFunctionCase.SamplerType {
				case "never":
					sampleFun = neverSampler
				case "always":
					sampleFun = alwaysSampler
				}
				sampler := NewCustomSampler(sampleFun)
				span := toReadonlySpan(scenario.InputSpan)
				sampler.SetConfig(&scenario.SamplingConfig)
				result := sampler.SampleSpan(span)
				if result.Sample != samplerFunctionCase.ExpectedResult.Sample {
					t.Errorf("Expected sample to be %v, got %v", samplerFunctionCase.ExpectedResult.Sample, result.Sample)
				}
				if len(result.Attributes) != len(samplerFunctionCase.ExpectedResult.Attributes) {
					t.Errorf("Expected attributes to be %v, got %v", samplerFunctionCase.ExpectedResult.Attributes, result.Attributes)
				}
				for key, value := range samplerFunctionCase.ExpectedResult.Attributes {
					found := false
					for _, attr := range result.Attributes {
						if string(attr.Key) == key {
							found = true
							if !compareValues(value, attr.Value.AsInt64()) {
								t.Errorf("Expected attribute %v to be %v, got %v", key, value, attr.Value.AsInt64())
							}
						}
					}
					if !found {
						t.Errorf("Expected attribute %v to be present", key)
					}
				}
			}
		})
	}
}

type logTestScenarios []logTestScenario

func toLogValue(key string, value any) log.Value {
	switch v := value.(type) {
	case string:
		return log.StringValue(v)
	case int:
		return log.IntValue(v)
	case int64:
		return log.Int64Value(v)
	case float64:
		return log.Float64Value(v)
	case bool:
		return log.BoolValue(v)
	default:
		// Fallback to string representation for unknown types
		panic(fmt.Sprintf("unknown type: %T", v))
	}
}

func toLogAttributes(inputLog inputLog) []log.KeyValue {
	attributes := make([]log.KeyValue, 0)
	for key, value := range inputLog.Attributes {
		attributes = append(attributes, log.KeyValue{Key: key, Value: toLogValue(key, value)})
	}
	return attributes
}

func toLogRecord(inputLog inputLog) sdklog.Record {
	factory := logtest.RecordFactory{
		AttributeValueLengthLimit: 1024, // Set a reasonable limit
		AttributeCountLimit:       100,  // Set a reasonable limit
	}

	// Set the message body
	if inputLog.Message != "" {
		factory.Body = log.StringValue(inputLog.Message)
	}

	// Set severity
	if inputLog.SeverityText != "" {
		factory.SeverityText = inputLog.SeverityText
	}

	// Set attributes
	if len(inputLog.Attributes) > 0 {
		factory.Attributes = toLogAttributes(inputLog)
	}

	return factory.NewRecord()
}

func TestLogScenarios(t *testing.T) {
	logTestScenarios := logTestScenarios{}
	logTestScenariosJSON, err := os.ReadFile("log-test-scenarios.json")

	if err != nil {
		t.Fatalf("Failed to read log-test-scenarios.json: %v", err)
	}

	err = json.Unmarshal(logTestScenariosJSON, &logTestScenarios)
	if err != nil {
		t.Fatalf("Failed to unmarshal log-test-scenarios.json: %v", err)
	}

	for _, scenario := range logTestScenarios {
		t.Run(scenario.Description, func(t *testing.T) {
			for _, samplerFunctionCase := range scenario.SamplerFunctionCases {
				var sampleFun SamplerFunc
				switch samplerFunctionCase.SamplerType {
				case "never":
					sampleFun = neverSampler
				case "always":
					sampleFun = alwaysSampler
				}
				sampler := NewCustomSampler(sampleFun)
				logRecord := toLogRecord(scenario.InputLog)
				sampler.SetConfig(&scenario.SamplingConfig)
				result := sampler.SampleLog(logRecord)
				if result.Sample != samplerFunctionCase.ExpectedResult.Sample {
					t.Errorf("Expected sample to be %v, got %v", samplerFunctionCase.ExpectedResult.Sample, result.Sample)
				}
				if len(result.Attributes) != len(samplerFunctionCase.ExpectedResult.Attributes) {
					t.Errorf("Expected attributes to be %v, got %v", samplerFunctionCase.ExpectedResult.Attributes, result.Attributes)
				}
				for key, value := range samplerFunctionCase.ExpectedResult.Attributes {
					found := false
					for _, attr := range result.Attributes {
						if string(attr.Key) == key {
							if !compareValues(value, attr.Value.AsInt64()) {
								t.Errorf("Expected attribute %v to be %v, got %v", key, value, attr.Value.AsInt64())
							}
							found = true
						}
					}
					if !found {
						t.Errorf("Expected attribute %v to be present", key)
					}
				}
			}
		})
	}
}
