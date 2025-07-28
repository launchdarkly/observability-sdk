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
	tracerProvider *sdktrace.TracerProvider
	loggerProvider *sdklog.LoggerProvider
	meterProvider  *sdkmetric.MeterProvider
}

type Config struct {
	otlpEndpoint       string
	resourceAttributes []attribute.KeyValue
}

func (o *OTLP) Shutdown() {
	ctx := context.Background()
	err := o.tracerProvider.ForceFlush(ctx)
	if err != nil {
		logging.Log.Error(err)
	}
	err = o.tracerProvider.Shutdown(ctx)
	if err != nil {
		logging.Log.Error(err)
	}
	err = o.loggerProvider.ForceFlush(ctx)
	if err != nil {
		logging.Log.Error(err)
	}
	err = o.loggerProvider.Shutdown(ctx)
	if err != nil {
		logging.Log.Error(err)
	}
	err = o.meterProvider.ForceFlush(ctx)
	if err != nil {
		logging.Log.Error(err)
	}
	err = o.meterProvider.Shutdown(ctx)
	if err != nil {
		logging.Log.Error(err)
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
	options, _, _ := getOTLPOptions(config.otlpEndpoint)
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
	_, options, _ := getOTLPOptions(config.otlpEndpoint)
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
	_, _, options := getOTLPOptions(config.otlpEndpoint)
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
	var defaultLoggerProvider *sdklog.LoggerProvider
	return &OTLP{
		tracerProvider: otel.GetTracerProvider().(*sdktrace.TracerProvider),
		loggerProvider: defaultLoggerProvider,
		meterProvider:  otel.GetMeterProvider().(*sdkmetric.MeterProvider),
	}
}

type OtelInstances struct {
	tracer trace.Tracer
	logger log.Logger
	meter  metric.Meter
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

var instances *atomic.Value = defaultInstancesValue()

func GetInstances() *OtelInstances {
	return instances.Load().(*OtelInstances)
}

func GetTracer() trace.Tracer {
	return GetInstances().tracer
}

func GetLogger() log.Logger {
	return GetInstances().logger
}

func GetMeter() metric.Meter {
	return GetInstances().meter
}

func setInstances(updated *OtelInstances) {
	instances.Store(updated)
}

func StartOTLP(config Config, sampler sdktrace.Sampler) (*OTLP, error) {
	ctx := context.Background()

	resources, err := resource.New(ctx,
		resource.WithFromEnv(),
		resource.WithHost(),
		resource.WithContainer(),
		resource.WithOS(),
		resource.WithProcess(),
		resource.WithAttributes(config.resourceAttributes...),
	)

	if err != nil {
		return nil, err
	}

	tracerProvider, err := CreateTracerProvider(ctx, config, resources, sampler)
	if err != nil {
		return nil, err
	}

	loggerProvider, err := CreateLoggerProvider(ctx, config, resources)
	if err != nil {
		return nil, err
	}

	meterProvider, err := CreateMeterProvider(ctx, config, resources)
	if err != nil {
		return nil, err
	}

	propagator := propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	)
	otel.SetTextMapPropagator(propagator)

	h := (&OTLP{tracerProvider: tracerProvider, loggerProvider: loggerProvider, meterProvider: meterProvider})
	newInstances := &OtelInstances{
		tracer: h.tracerProvider.Tracer(
			metadata.InstrumentationName,
			trace.WithInstrumentationVersion(metadata.InstrumentationVersion),
			trace.WithSchemaURL(semconv.SchemaURL),
		),
		logger: h.loggerProvider.Logger(
			metadata.InstrumentationName,
			log.WithInstrumentationVersion(metadata.InstrumentationVersion),
			log.WithSchemaURL(semconv.SchemaURL),
		),
		meter: h.meterProvider.Meter(
			metadata.InstrumentationName,
			metric.WithInstrumentationVersion(metadata.InstrumentationVersion),
			metric.WithSchemaURL(semconv.SchemaURL),
		),
	}
	setInstances(newInstances)

	otel.SetTracerProvider(tracerProvider)
	otel.SetMeterProvider(meterProvider)

	return h, nil
}
