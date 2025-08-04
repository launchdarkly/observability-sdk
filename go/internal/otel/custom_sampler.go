package otel

import (
	"math/rand"
	"regexp"
	"sync"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/log"
	"go.opentelemetry.io/otel/sdk/trace"

	"github.com/launchdarkly/observability-sdk/go/internal/gql"

	"github.com/launchdarkly/observability-sdk/go/attributes"
)

// ReadonlySpanSubset implements the components of the trace.ReadonlySpan
// that are needed for sampling.
// The trace.ReadonlySpan interface cannot be directly implemented, because
// it contains a `private()` method. So we define this subset so we can
// make instances in unit tests.
type ReadonlySpanSubset interface {
	// Events returns all the events that occurred within in the spans
	// lifetime.
	Events() []trace.Event
	// Name returns the name of the span.
	Name() string
	// Attributes returns the defining attributes of the span.
	// The order of the returned attributes is not guaranteed to be stable across invocations.
	Attributes() []attribute.KeyValue
}

// SamplingResult represents the result of sampling a span or log
type SamplingResult struct {
	// Sample indicates whether the span/log should be sampled
	Sample bool
	// Attributes contains additional attributes to add to the span/log
	Attributes map[string]interface{}
}

type matchParts interface {
	GetMatchValue() interface{}
	GetRegexValue() string
}

// ExportSampler defines the interface for export samplers
type ExportSampler interface {
	// SampleSpan samples a span and returns the result
	SampleSpan(span ReadonlySpanSubset) SamplingResult
	// SampleLog samples a log record and returns the result
	SampleLog(record log.Record) SamplingResult
	// IsSamplingEnabled returns true if sampling is enabled
	IsSamplingEnabled() bool
	// SetConfig sets the sampling configuration
	SetConfig(config *gql.GetSamplingConfigSamplingSamplingConfig)
}

// SamplerFunc is a function type for sampling decisions
type SamplerFunc func(ratio int) bool

// DefaultSampler determines if an item should be sampled based on the sampling
// ratio.
// This function is not used for any purpose requiring cryptographic security.
func DefaultSampler(ratio int) bool {
	// A ratio of 1 means 1 in 1. So that will always sample. No need
	// to draw a random number.
	if ratio == 1 {
		return true
	}
	// A ratio of 0 means 0 in 1. So that will never sample.
	if ratio == 0 {
		return false
	}

	// rand.Float64() * ratio would return 0, 1, ... (ratio - 1).
	// Checking for any number in the range will have approximately a 1 in X
	// chance. So we check for 0 as it is part of any range.
	//nolint:gosec // This is not used for cryptographic security.
	return int(rand.Float64()*float64(ratio)) == 0
}

// implMatchParts is a wrapper for use when matching raw value types.
// Such as wrapping the values in a slice.
type implMatchParts struct {
	matchValue interface{}
	regexValue string
}

func (m *implMatchParts) GetMatchValue() interface{} {
	return m.matchValue
}

func (m *implMatchParts) GetRegexValue() string {
	return m.regexValue
}

func toMatchParts(value interface{}) matchParts {
	return &implMatchParts{
		matchValue: value,
	}
}

// CustomSampler is a custom sampler that uses sampling configuration to
// determine if a span should be sampled
type CustomSampler struct {
	sampler    SamplerFunc
	config     *gql.GetSamplingConfigSamplingSamplingConfig
	regexCache map[string]*regexp.Regexp
	mutex      sync.RWMutex
	regexMutex sync.RWMutex
}

// NewCustomSampler creates a new CustomSampler with the given sampler function
func NewCustomSampler(sampler SamplerFunc) *CustomSampler {
	if sampler == nil {
		sampler = DefaultSampler
	}
	return &CustomSampler{
		sampler:    sampler,
		regexCache: make(map[string]*regexp.Regexp),
	}
}

// SetConfig sets the sampling configuration
func (cs *CustomSampler) SetConfig(config *gql.GetSamplingConfigSamplingSamplingConfig) {
	cs.mutex.Lock()
	defer cs.mutex.Unlock()
	cs.config = config
}

// IsSamplingEnabled returns true if sampling is enabled
func (cs *CustomSampler) IsSamplingEnabled() bool {
	cs.mutex.RLock()
	defer cs.mutex.RUnlock()
	if cs.config != nil && (len(cs.config.Spans) > 0 || len(cs.config.Logs) > 0) {
		return true
	}
	return false
}

// getCachedRegex gets a cached regex pattern
func (cs *CustomSampler) getCachedRegex(pattern string) (*regexp.Regexp, error) {
	result := getExitingCachedRegex(cs, pattern)
	if result != nil {
		return result, nil
	}

	// There was not a cached regex, so we need to compile and cache it.
	cs.regexMutex.Lock()
	defer cs.regexMutex.Unlock()

	// Between checking and here we had to release the lock, so we need to check
	// again to see if someone else has already compiled and cached the regex.
	if regex, exists := cs.regexCache[pattern]; exists {
		return regex, nil
	}

	compiled, err := regexp.Compile(pattern)
	if err != nil {
		return nil, err
	}

	cs.regexCache[pattern] = compiled

	return compiled, nil
}

func getExitingCachedRegex(cs *CustomSampler, pattern string) *regexp.Regexp {
	cs.regexMutex.RLock()
	defer cs.regexMutex.RUnlock()

	// Will always be locked by the caller.
	if regex, exists := cs.regexCache[pattern]; exists {
		return regex
	}

	return nil
}

type matchable interface {
	attribute.Value | attribute.Key | string | log.Value
}

// matchesValue matches a value against a match configuration
func matchesValue[V matchable](cs *CustomSampler, matchConfig matchParts, value V) bool {
	if matchConfig == nil {
		return false
	}

	matchValue := matchConfig.GetMatchValue()
	// Check if it's a basic match config (has MatchValue)
	if matchValue != nil {
		switch v := any(value).(type) {
		case attribute.Value:
			b := matchAttributeValue(v, matchValue)
			return b
		case attribute.Key:
			asString, ok := matchValue.(string)
			if !ok {
				return false
			}
			return asString == string(v)
		case log.Value:
			b := matchLogValue(v, matchValue, cs)
			return b
		case string:
			asString, ok := matchValue.(string)
			if !ok {
				return false
			}
			return asString == v
		default:
			return false
		}
	}

	regexValue := matchConfig.GetRegexValue()
	if regexValue != "" {
		regex, err := cs.getCachedRegex(regexValue)
		if err != nil {
			return false
		}
		switch v := any(value).(type) {
		case attribute.Value:
			if v.Type() == attribute.STRING {
				return regex.MatchString(v.AsString())
			}
			return false
		case attribute.Key:
			return regex.MatchString(string(v))
		case log.Value:
			if v.Kind() == log.KindString {
				return regex.MatchString(v.AsString())
			}
			return false
		case string:
			return regex.MatchString(v)
		default:
			return false
		}
	}

	return false
}

func matchLogValue(v log.Value, matchValue interface{}, cs *CustomSampler) bool {
	switch v.Kind() {
	case log.KindBool:
		asBool, ok := matchValue.(bool)
		if !ok {
			return false
		}
		return asBool == v.AsBool()
	case log.KindInt64:
		asInt64, ok := matchValue.(int64)
		if !ok {
			return false
		}
		return asInt64 == v.AsInt64()
	case log.KindFloat64:
		asFloat64, ok := matchValue.(float64)
		if !ok {
			return false
		}
		return asFloat64 == v.AsFloat64()
	case log.KindString:
		asString, ok := matchValue.(string)
		if !ok {
			return false
		}
		return asString == v.AsString()
	case log.KindSlice:
		asSlice, ok := matchValue.([]any)
		if !ok {
			return false
		}
		lenMatch := len(asSlice) == len(v.AsSlice())
		if !lenMatch {
			return false
		}
		for i := range asSlice {
			if !matchesValue(cs, toMatchParts(asSlice[i]), v.AsSlice()[i]) {
				return false
			}
		}
		return true
	default:
		return false
	}
}

func matchAttributeValue(v attribute.Value, matchValue interface{}) bool {
	switch v.Type() {
	case attribute.BOOL:
		asBool, ok := matchValue.(bool)
		if !ok {
			return false
		}
		return asBool == v.AsBool()
	case attribute.INT64:
		asInt64, ok := matchValue.(int64)
		if !ok {
			return false
		}
		return asInt64 == v.AsInt64()
	case attribute.FLOAT64:
		asFloat64, ok := matchValue.(float64)
		if !ok {
			return false
		}
		return asFloat64 == v.AsFloat64()
	case attribute.STRING:
		asString, ok := matchValue.(string)
		if !ok {
			return false
		}
		return asString == v.AsString()
	case attribute.BOOLSLICE:
		asBoolSlice, ok := matchValue.([]bool)
		if !ok {
			return false
		}
		lenMatch := len(asBoolSlice) == len(v.AsBoolSlice())
		if !lenMatch {
			return false
		}
		for i := range asBoolSlice {
			if asBoolSlice[i] != v.AsBoolSlice()[i] {
				return false
			}
		}
		return true
	case attribute.INT64SLICE:
		asInt64Slice, ok := matchValue.([]int64)
		if !ok {
			return false
		}
		lenMatch := len(asInt64Slice) == len(v.AsInt64Slice())
		if !lenMatch {
			return false
		}
		for i := range asInt64Slice {
			if asInt64Slice[i] != v.AsInt64Slice()[i] {
				return false
			}
		}
		return true
	case attribute.FLOAT64SLICE:
		asFloat64Slice, ok := matchValue.([]float64)
		if !ok {
			return false
		}
		lenMatch := len(asFloat64Slice) == len(v.AsFloat64Slice())
		if !lenMatch {
			return false
		}
		for i := range asFloat64Slice {
			if asFloat64Slice[i] != v.AsFloat64Slice()[i] {
				return false
			}
		}
		return true
	default:
		return false
	}
}

// matchesAttributes matches span attributes against configuration
func matchesAttributes(
	cs *CustomSampler,
	attributeConfigs []gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigAttributesAttributeMatchConfig,
	spanAttributes []attribute.KeyValue,
) bool {
	if len(attributeConfigs) == 0 {
		return true
	}

	if len(spanAttributes) == 0 {
		return false
	}

	for _, attrConfig := range attributeConfigs {
		configMatched := false
		for _, attr := range spanAttributes {
			if matchesValue(cs, &attrConfig.Key, attr.Key) {
				if matchesValue(cs, &attrConfig.Attribute, attr.Value) {
					configMatched = true
					break
				}
			}
		}
		if !configMatched {
			return false
		}
	}

	return true
}

func matchesAttributesLogs(
	cs *CustomSampler,
	attributeConfigs []gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfigAttributesAttributeMatchConfig,
	record log.Record,
) bool {
	if len(attributeConfigs) == 0 {
		return true
	}

	logAttributes := make([]log.KeyValue, 0)

	record.WalkAttributes(func(kv log.KeyValue) bool {
		logAttributes = append(logAttributes, kv)
		return true
	})

	if len(logAttributes) == 0 {
		return false
	}

	for _, attrConfig := range attributeConfigs {
		configMatched := false
		for _, attr := range logAttributes {
			if matchesValue(cs, &attrConfig.Key, attr.Key) {
				if matchesValue(cs, &attrConfig.Attribute, attr.Value) {
					configMatched = true
					break
				}
			}
		}
		if !configMatched {
			return false
		}
	}

	return true
}

// matchesEventAttributes matches event attributes against configuration
func matchesEventAttributes(
	cs *CustomSampler,
	//nolint:lll // Generated type is longer than the the lint line length.
	attributeConfigs []gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigEventsSpanEventMatchConfigAttributesAttributeMatchConfig,
	eventAttributes []attribute.KeyValue,
) bool {
	if len(attributeConfigs) == 0 {
		return true
	}

	if len(eventAttributes) == 0 {
		return false
	}

	for _, attrConfig := range attributeConfigs {
		configMatched := false
		for _, attr := range eventAttributes {
			if matchesValue(cs, &attrConfig.Key, attr.Key) {
				if matchesValue(cs, &attrConfig.Attribute, attr.Value) {
					configMatched = true
					break
				}
			}
		}
		if !configMatched {
			return false
		}
	}

	return true
}

// isMatchConfigEmpty checks if a match config is empty (has no match value or regex value)
func isMatchConfigEmpty(matchConfig interface{}) bool {
	if matchConfig == nil {
		return true
	}

	// Check if it has a non-empty match value
	if matchParts, ok := matchConfig.(interface{ GetMatchValue() interface{} }); ok {
		if matchValue := matchParts.GetMatchValue(); matchValue != nil {
			return false
		}
	}

	// Check if it has a non-empty regex value
	if matchParts, ok := matchConfig.(interface{ GetRegexValue() string }); ok {
		if regexValue := matchParts.GetRegexValue(); regexValue != "" {
			return false
		}
	}

	return true
}

// matchEvent matches a single event against an event configuration
func (cs *CustomSampler) matchEvent(
	eventConfig *gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigEventsSpanEventMatchConfig,
	event trace.Event,
) bool {
	// Match by event name if specified
	if nameConfig := eventConfig.GetName(); !isMatchConfigEmpty(&nameConfig) {
		if !matchesValue(cs, &nameConfig, event.Name) {
			return false
		}
	}

	// Match by event attributes if specified
	if attrConfigs := eventConfig.GetAttributes(); len(attrConfigs) > 0 {
		if !matchesEventAttributes(cs, attrConfigs, event.Attributes) {
			return false
		}
	}

	return true
}

// matchesEvents matches span events against event configurations
func (cs *CustomSampler) matchesEvents(
	eventConfigs []gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfigEventsSpanEventMatchConfig,
	events []trace.Event,
) bool {
	if len(eventConfigs) == 0 {
		return true
	}

	for _, eventConfig := range eventConfigs {
		matched := false
		for _, event := range events {
			if cs.matchEvent(&eventConfig, event) {
				matched = true
				// We only need a single event to match each config
				break
			}
		}
		if !matched {
			return false
		}
	}

	return true
}

// matchesSpanConfig matches a span against the configuration
func (cs *CustomSampler) matchesSpanConfig(
	config *gql.GetSamplingConfigSamplingSamplingConfigSpansSpanSamplingConfig,
	span ReadonlySpanSubset,
) bool {
	// Check span name if defined
	if nameConfig := config.GetName(); !isMatchConfigEmpty(&nameConfig) {
		if !matchesValue(cs, &nameConfig, span.Name()) {
			return false
		}
	}

	if !matchesAttributes(cs, config.Attributes, span.Attributes()) {
		return false
	}

	// Check events
	if !cs.matchesEvents(config.GetEvents(), span.Events()) {
		return false
	}

	return true
}

// matchesLogConfig matches a log record against the configuration
func (cs *CustomSampler) matchesLogConfig(
	config *gql.GetSamplingConfigSamplingSamplingConfigLogsLogSamplingConfig,
	record log.Record,
) bool {
	// Check severity text if defined
	if severityConfig := config.GetSeverityText(); !isMatchConfigEmpty(&severityConfig) {
		if !matchesValue(cs, &severityConfig, record.SeverityText()) {
			return false
		}
	}

	// Check message if defined
	if messageConfig := config.GetMessage(); !isMatchConfigEmpty(&messageConfig) {
		if record.Body().Kind() == log.KindString {
			if !matchesValue(cs, &messageConfig, record.Body().AsString()) {
				return false
			}
		}
	}

	if attributeConfig := config.GetAttributes(); len(attributeConfig) > 0 {
		if !matchesAttributesLogs(cs, attributeConfig, record) {
			return false
		}
	}

	return true
}

// SampleSpan samples a span based on the sampling configuration
func (cs *CustomSampler) SampleSpan(span ReadonlySpanSubset) SamplingResult {
	cs.mutex.RLock()
	defer cs.mutex.RUnlock()

	if cs.config != nil && len(cs.config.Spans) > 0 {
		for _, spanConfig := range cs.config.Spans {
			if cs.matchesSpanConfig(&spanConfig, span) {
				return SamplingResult{
					Sample: cs.sampler(spanConfig.GetSamplingRatio()),
					Attributes: map[string]interface{}{
						attributes.AttrSamplingRatio: spanConfig.GetSamplingRatio(),
					},
				}
			}
		}
	}

	// Didn't match any sampling config, or there were no configs, so we sample it
	return SamplingResult{Sample: true}
}

// SampleLog samples a log record based on the sampling configuration
func (cs *CustomSampler) SampleLog(record log.Record) SamplingResult {
	cs.mutex.RLock()
	defer cs.mutex.RUnlock()

	if cs.config != nil && len(cs.config.Logs) > 0 {
		for _, logConfig := range cs.config.Logs {
			if cs.matchesLogConfig(&logConfig, record) {
				return SamplingResult{
					Sample: cs.sampler(logConfig.GetSamplingRatio()),
					Attributes: map[string]interface{}{
						attributes.AttrSamplingRatio: logConfig.GetSamplingRatio(),
					},
				}
			}
		}
	}

	// Didn't match any sampling config, or there were no configs, so we sample it
	return SamplingResult{Sample: true}
}
