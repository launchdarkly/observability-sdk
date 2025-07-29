package otel

import (
	"context"
	"fmt"
	"strings"
	"sync/atomic"
	"time"

	"github.com/launchdarkly/observability-sdk/go/internal/logging"
	"github.com/launchdarkly/observability-sdk/go/internal/metadata"
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
)

type OTLP struct {
	tracerProvider trace.TracerProvider
	loggerProvider log.LoggerProvider
	meterProvider  metric.MeterProvider
}

type OtelInstances struct {
	tracer trace.Tracer
	logger log.Logger
	meter  metric.Meter
}

type Config struct {
	OtlpEndpoint       string
	ResourceAttributes []attribute.KeyValue
}

func defaultInstancesValue() *atomic.Value {
	v := &atomic.Value{}
	providers := GetDefaultProviders()
	instances := &OtelInstances{
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

// Instances are stores in an atomic so that they can have consistent visibility
// within goroutines without using locks. If this was not an atomic then goroutines
// which start before the OTLP configuration was complete may never have visibility
// of the current values.
//
// The similar otel functions, such as otel.Tracer, also use atomics to store the
// current values.
var instances *atomic.Value = defaultInstancesValue()

// Currently active OTLP instance. This is set when StartOTLP is called.
var otlp atomic.Pointer[OTLP]

func Shutdown() {
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

func getOTLPOptions(endpoint string) (traceOpts []otlptracehttp.Option, logOpts []otlploghttp.Option, metricOpts []otlpmetrichttp.Option) {
	if strings.HasPrefix(endpoint, "http://") {
		traceOpts = append(traceOpts, otlptracehttp.WithEndpoint(endpoint[7:]), otlptracehttp.WithInsecure())
		logOpts = append(logOpts, otlploghttp.WithEndpoint(endpoint[7:]), otlploghttp.WithInsecure())
		metricOpts = append(metricOpts, otlpmetrichttp.WithEndpoint(endpoint[7:]), otlpmetrichttp.WithInsecure())
	} else if strings.HasPrefix(endpoint, "https://") {
		traceOpts = append(traceOpts, otlptracehttp.WithEndpoint(endpoint[8:]))
		logOpts = append(logOpts, otlploghttp.WithEndpoint(endpoint[8:]))
		metricOpts = append(metricOpts, otlpmetrichttp.WithEndpoint(endpoint[8:]))
	} else {
		logging.Log.Errorf("an invalid otlp endpoint was configured %s", endpoint)
	}
	traceOpts = append(traceOpts, otlptracehttp.WithCompression(otlptracehttp.GzipCompression))
	logOpts = append(logOpts, otlploghttp.WithCompression(otlploghttp.GzipCompression))
	metricOpts = append(metricOpts, otlpmetrichttp.WithCompression(otlpmetrichttp.GzipCompression))
	return
}

func CreateTracerProvider(ctx context.Context, config Config, resources *resource.Resource, sampler sdktrace.Sampler, opts ...sdktrace.TracerProviderOption) (*sdktrace.TracerProvider, error) {
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

func CreateLoggerProvider(ctx context.Context, config Config, resources *resource.Resource, opts ...sdklog.LoggerProviderOption) (*sdklog.LoggerProvider, error) {
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

func CreateMeterProvider(ctx context.Context, config Config, resources *resource.Resource, opts ...sdkmetric.Option) (*sdkmetric.MeterProvider, error) {
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

func GetDefaultProviders() *OTLP {
	var defaultTracerProvider = otel.GetTracerProvider()
	var defaultMeterProvider = otel.GetMeterProvider()
	var defaultLoggerProvider = sdklog.NewLoggerProvider()
	return &OTLP{
		tracerProvider: defaultTracerProvider,
		loggerProvider: defaultLoggerProvider,
		meterProvider:  defaultMeterProvider,
	}
}

// GetTracer returns the current tracer instance. The result of this function
// should not be cached unless the caller is sure that OTLP has been started.
func GetTracer() trace.Tracer {
	return instances.Load().(*OtelInstances).tracer
}

// GetLogger returns the current logger instance. The result of this function
// should not be cached unless the caller is sure that OTLP has been started.
func GetLogger() log.Logger {
	return instances.Load().(*OtelInstances).logger
}

// GetMeter returns the current meter instance. The result of this function
// should not be cached unless the caller is sure that OTLP has been started.
func GetMeter() metric.Meter {
	return instances.Load().(*OtelInstances).meter
}

// StartOTLP configures otel to send data to the LaunchDarkly OTLP endpoints.
// Under ideal use this function is called once at startup.
// The Shutdown function should be called when the application is shutting down
// to ensure delivery of any pending events.
func StartOTLP(config Config, sampler sdktrace.Sampler) error {
	Shutdown()
	ctx := context.Background()

	resources, err := resource.New(ctx,
		resource.WithFromEnv(),
		resource.WithHost(),
		resource.WithContainer(),
		resource.WithOS(),
		resource.WithProcess(),
		resource.WithAttributes(config.ResourceAttributes...),
	)

	if err != nil {
		return err
	}

	tracerProvider, err := CreateTracerProvider(ctx, config, resources, sampler)
	if err != nil {
		return err
	}

	loggerProvider, err := CreateLoggerProvider(ctx, config, resources)
	if err != nil {
		return err
	}

	meterProvider, err := CreateMeterProvider(ctx, config, resources)
	if err != nil {
		return err
	}

	propagator := propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	)
	otel.SetTextMapPropagator(propagator)

	o := &OTLP{tracerProvider: tracerProvider, loggerProvider: loggerProvider, meterProvider: meterProvider}
	newInstances := &OtelInstances{
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
