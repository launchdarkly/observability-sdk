package otel

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploghttp"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/log"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/propagation"
	sdklog "go.opentelemetry.io/otel/sdk/log"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.4.0"
	"go.opentelemetry.io/otel/trace"

	"github.com/launchdarkly/observability-sdk/go/internal/logging"
	"github.com/launchdarkly/observability-sdk/go/internal/metadata"
)

type providerInstances struct {
	tracerProvider trace.TracerProvider
	loggerProvider log.LoggerProvider
	meterProvider  metric.MeterProvider
}

type otelInstances struct {
	tracer trace.Tracer
	logger log.Logger
	meter  metric.Meter
}

// Config contains the configuration for the OTLP provider.
type Config struct {
	OtlpEndpoint       string
	ResourceAttributes []attribute.KeyValue
	Sampler            sdktrace.Sampler
}

func defaultInstancesValue() *atomic.Value {
	v := &atomic.Value{}
	providers := getDefaultProviders()
	instances := &otelInstances{
		tracer: providers.tracerProvider.Tracer(
			metadata.InstrumentationName,
			trace.WithInstrumentationVersion(metadata.InstrumentationVersion),
			trace.WithSchemaURL(semconv.SchemaURL),
		),
		logger: providers.loggerProvider.Logger(
			metadata.InstrumentationName,
			log.WithInstrumentationVersion(metadata.InstrumentationVersion),
			log.WithSchemaURL(semconv.SchemaURL),
		),
		meter: providers.meterProvider.Meter(
			metadata.InstrumentationName,
			metric.WithInstrumentationVersion(metadata.InstrumentationVersion),
			metric.WithSchemaURL(semconv.SchemaURL),
		),
	}
	v.Store(instances)
	return v
}

// writeLock is used to synchronize access to the instances and config atomic values.
// Starting or stopping OTLP should be done with this lock held.
// Access to the OTLP instances should be done with the instances atomic value and
// does not require holding the lock.
//
//nolint:gochecknoglobals
var writeLock sync.Mutex

// Instances are stores in an atomic so that they can have consistent visibility
// within goroutines without using locks. If this was not an atomic then goroutines
// which start before the OTLP configuration was complete may never have visibility
// of the current values.
//
// The similar otel functions, such as otel.Tracer, also use atomics to store the
// current values.
//
//nolint:gochecknoglobals
var instances *atomic.Value = defaultInstancesValue()

// Currently active OTLP instance. This is set when StartOTLP is called.
//
//nolint:gochecknoglobals
var otlp atomic.Pointer[providerInstances]

// Currently active configuration. The configuration is stored to allow manual starting
// after the plugin is registered.
//
//nolint:gochecknoglobals
var config atomic.Pointer[Config]

// Shutdown flushes pending data and shuts down the OTLP instances.
func Shutdown() {
	writeLock.Lock()
	end := func() {
		writeLock.Unlock()
	}
	shutdown()
	end()
}

func shutdown() {
	// Get the current OTLP instance and set it to nil.
	o := otlp.Swap(nil)
	if o == nil {
		return
	}
	ctx := context.Background()
	tp := o.tracerProvider.(*sdktrace.TracerProvider)
	mp := o.meterProvider.(*sdkmetric.MeterProvider)
	lp := o.loggerProvider.(*sdklog.LoggerProvider)

	// The SDK instances of the various providers have additional methods versus the
	// interfaces. The default implementations only implement the interface, and
	// we cast to these concrete types to be able to call the additional methods.
	if tp != nil {
		err := tp.ForceFlush(ctx)
		if err != nil {
			logging.Log.Error(err)
		}
		err = tp.Shutdown(ctx)
		if err != nil {
			logging.Log.Error(err)
		}
	}

	if lp != nil {
		err := lp.ForceFlush(ctx)
		if err != nil {
			logging.Log.Error(err)
		}
		err = lp.Shutdown(ctx)
		if err != nil {
			logging.Log.Error(err)
		}
	}

	if mp != nil {
		err := mp.ForceFlush(ctx)
		if err != nil {
			logging.Log.Error(err)
		}
		err = mp.Shutdown(ctx)
		if err != nil {
			logging.Log.Error(err)
		}
	}
}

func getOTLPOptions(endpoint string) (
	traceOpts []otlptracehttp.Option,
	logOpts []otlploghttp.Option,
	metricOpts []otlpmetrichttp.Option,
) {
	switch {
	case strings.HasPrefix(endpoint, "http://"):
		traceOpts = append(traceOpts, otlptracehttp.WithEndpoint(endpoint[7:]), otlptracehttp.WithInsecure())
		logOpts = append(logOpts, otlploghttp.WithEndpoint(endpoint[7:]), otlploghttp.WithInsecure())
		metricOpts = append(metricOpts, otlpmetrichttp.WithEndpoint(endpoint[7:]), otlpmetrichttp.WithInsecure())
	case strings.HasPrefix(endpoint, "https://"):
		traceOpts = append(traceOpts, otlptracehttp.WithEndpoint(endpoint[8:]))
		logOpts = append(logOpts, otlploghttp.WithEndpoint(endpoint[8:]))
		metricOpts = append(metricOpts, otlpmetrichttp.WithEndpoint(endpoint[8:]))
	default:
		logging.Log.Errorf("an invalid otlp endpoint was configured %s", endpoint)
	}
	traceOpts = append(traceOpts, otlptracehttp.WithCompression(otlptracehttp.GzipCompression))
	logOpts = append(logOpts, otlploghttp.WithCompression(otlploghttp.GzipCompression))
	metricOpts = append(metricOpts, otlpmetrichttp.WithCompression(otlpmetrichttp.GzipCompression))
	return
}

func createTracerProvider(
	ctx context.Context,
	config *Config,
	resources *resource.Resource,
	sampler sdktrace.Sampler,
	opts ...sdktrace.TracerProviderOption,
) (*sdktrace.TracerProvider, error) {
	options, _, _ := getOTLPOptions(config.OtlpEndpoint)
	client := otlptracehttp.NewClient(options...)
	exporter, err := otlptrace.New(ctx, client)
	if err != nil {
		return nil, fmt.Errorf("creating OTLP trace exporter: %w", err)
	}
	opts = append([]sdktrace.TracerProviderOption{
		sdktrace.WithSampler(sampler),
		sdktrace.WithBatcher(exporter,
			sdktrace.WithBatchTimeout(time.Second),
			sdktrace.WithExportTimeout(30*time.Second),
			sdktrace.WithMaxExportBatchSize(1024*1024),
			sdktrace.WithMaxQueueSize(1024*1024),
		),
		sdktrace.WithResource(resources),
	}, opts...)
	return sdktrace.NewTracerProvider(opts...), nil
}

func createLoggerProvider(
	ctx context.Context,
	config *Config,
	resources *resource.Resource,
	opts ...sdklog.LoggerProviderOption,
) (*sdklog.LoggerProvider, error) {
	_, options, _ := getOTLPOptions(config.OtlpEndpoint)
	exporter, err := otlploghttp.New(ctx, options...)
	if err != nil {
		return nil, fmt.Errorf("creating OTLP trace exporter: %w", err)
	}
	opts = append([]sdklog.LoggerProviderOption{
		sdklog.WithProcessor(sdklog.NewBatchProcessor(exporter,
			sdklog.WithExportTimeout(30*time.Second),
			sdklog.WithExportMaxBatchSize(1024*1024),
			sdklog.WithMaxQueueSize(1024*1024),
		)),
		sdklog.WithResource(resources),
	}, opts...)
	return sdklog.NewLoggerProvider(opts...), nil
}

func createMeterProvider(
	ctx context.Context,
	config *Config,
	resources *resource.Resource,
	opts ...sdkmetric.Option,
) (*sdkmetric.MeterProvider, error) {
	_, _, options := getOTLPOptions(config.OtlpEndpoint)
	exporter, err := otlpmetrichttp.New(ctx, options...)
	if err != nil {
		return nil, fmt.Errorf("creating OTLP trace exporter: %w", err)
	}
	opts = append([]sdkmetric.Option{
		sdkmetric.WithReader(sdkmetric.NewPeriodicReader(exporter,
			sdkmetric.WithInterval(5*time.Second),
			sdkmetric.WithTimeout(30*time.Second),
		)),
		sdkmetric.WithResource(resources),
	}, opts...)
	return sdkmetric.NewMeterProvider(opts...), nil
}

func getDefaultProviders() *providerInstances {
	var defaultTracerProvider = otel.GetTracerProvider()
	var defaultMeterProvider = otel.GetMeterProvider()
	var defaultLoggerProvider = sdklog.NewLoggerProvider()
	return &providerInstances{
		tracerProvider: defaultTracerProvider,
		loggerProvider: defaultLoggerProvider,
		meterProvider:  defaultMeterProvider,
	}
}

// GetTracer returns the current tracer instance. The result of this function
// should not be cached unless the caller is sure that OTLP has been started.
func GetTracer() trace.Tracer {
	return instances.Load().(*otelInstances).tracer
}

// GetLogger returns the current logger instance. The result of this function
// should not be cached unless the caller is sure that OTLP has been started.
func GetLogger() log.Logger {
	return instances.Load().(*otelInstances).logger
}

// GetMeter returns the current meter instance. The result of this function
// should not be cached unless the caller is sure that OTLP has been started.
func GetMeter() metric.Meter {
	return instances.Load().(*otelInstances).meter
}

// SetConfig sets the configuration for the OTLP provider.
// The configuration must be set before calling StartOTLP.
func SetConfig(conf Config) {
	writeLock.Lock()
	defer writeLock.Unlock()

	config.Store(&conf)
}

// StartOTLP configures otel to send data to the LaunchDarkly OTLP endpoints.
// Under ideal use this function is called once at startup.
// The Shutdown function should be called when the application is shutting down
// to ensure delivery of any pending events.
func StartOTLP() error {
	writeLock.Lock()
	defer writeLock.Unlock()

	// If the OTLP instance is already started, do nothing.
	if otlp.Load() != nil {
		return nil
	}

	conf := config.Load()

	if conf == nil {
		// This would represent an implementation error.
		return fmt.Errorf("ensure plugin is configured before calling StartOTLP")
	}

	shutdown()
	ctx := context.Background()

	resources, err := resource.New(ctx,
		resource.WithFromEnv(),
		resource.WithHost(),
		resource.WithContainer(),
		resource.WithOS(),
		resource.WithProcess(),
		resource.WithAttributes(conf.ResourceAttributes...),
	)

	if err != nil {
		return err
	}

	tracerProvider, err := createTracerProvider(ctx, conf, resources, conf.Sampler)
	if err != nil {
		return err
	}

	loggerProvider, err := createLoggerProvider(ctx, conf, resources)
	if err != nil {
		return err
	}

	meterProvider, err := createMeterProvider(ctx, conf, resources)
	if err != nil {
		return err
	}

	propagator := propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	)
	otel.SetTextMapPropagator(propagator)

	o := &providerInstances{tracerProvider: tracerProvider, loggerProvider: loggerProvider, meterProvider: meterProvider}
	newInstances := &otelInstances{
		tracer: o.tracerProvider.Tracer(
			metadata.InstrumentationName,
			trace.WithInstrumentationVersion(metadata.InstrumentationVersion),
			trace.WithSchemaURL(semconv.SchemaURL),
		),
		logger: o.loggerProvider.Logger(
			metadata.InstrumentationName,
			log.WithInstrumentationVersion(metadata.InstrumentationVersion),
			log.WithSchemaURL(semconv.SchemaURL),
		),
		meter: o.meterProvider.Meter(
			metadata.InstrumentationName,
			metric.WithInstrumentationVersion(metadata.InstrumentationVersion),
			metric.WithSchemaURL(semconv.SchemaURL),
		),
	}
	otlp.Store(o)
	instances.Store(newInstances)

	otel.SetTracerProvider(tracerProvider)
	otel.SetMeterProvider(meterProvider)

	return nil
}
