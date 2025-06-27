import os
import time
import random

from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor


HIGHLIGHT_PROJECT_ENV = "HIGHLIGHT_PROJECT_ID"
# OTLP_ENDPOINT = "otel.observability.app.launchdarkly.com:4317"
OTLP_ENDPOINT = "otel.observability.ld-stg.launchdarkly.com:4317"


def init_tracer():
    """Initialise the OpenTelemetry tracer with an OTLP gRPC exporter.

    The exporter is configured to send traces to the LaunchDarkly Observability
    OTLP endpoint and includes an ``x-highlight-project`` header so that the
    backend can associate the traces with the correct Highlight project.
    """
    project_id = os.getenv(HIGHLIGHT_PROJECT_ENV)
    if not project_id:
        raise RuntimeError(
            f"Environment variable '{HIGHLIGHT_PROJECT_ENV}' must be set to your highlight project id."
        )

    # Configure the OTLP/gRPC exporter with the custom header.
    exporter = OTLPSpanExporter(
        endpoint=OTLP_ENDPOINT,
        # TLS is enabled by default. If you need to disable certificate
        # verification, set ``insecure=True`` instead.
        headers={"x-highlight-project": project_id},
    )

    resource = Resource.create({
        "service.name": "highlight-otel-python-example",
        "service.version": "0.1.0",
    })

    provider = TracerProvider(resource=resource)
    processor = BatchSpanProcessor(exporter)
    provider.add_span_processor(processor)

    # Set the global tracer provider so that it is picked up by instrumentation.
    trace.set_tracer_provider(provider)
    return trace.get_tracer(__name__)


def _do_work(tracer, depth: int, breadth: int, base_delay: float):
    """Recursively create a tree of spans.

    Args:
        tracer: The OpenTelemetry tracer.
        depth: Current recursion depth remaining.
        breadth: Number of child spans to create at this depth.
        base_delay: Base delay in seconds used to generate a random sleep duration.
    """
    if depth <= 0:
        # Simulate some leaf-level work.
        time.sleep(random.uniform(0, base_delay))
        return

    for i in range(breadth):
        span_name = f"work-depth{depth}-branch{i}"
        with tracer.start_as_current_span(span_name) as span:
            span.set_attribute("depth", depth)
            span.set_attribute("branch", i)

            # Simulate variable work inside this span.
            inner_delay = random.uniform(0, base_delay)
            span.add_event("start_sleep", {"delay": inner_delay})
            time.sleep(inner_delay)
            span.add_event("end_sleep")

            # Recurse to produce child spans.
            _do_work(tracer, depth - 1, breadth, base_delay / 1.5)


def generate_complex_workload(tracer):
    """Generate a complex span tree meant to visualise in a flame graph."""
    ROOT_ITERATIONS = 100  # Number of root-level operations.
    DEPTH = 8            # Depth of recursion (creates DEPTH+1 levels of spans).
    BREADTH = 10          # Number of child spans per level.
    BASE_DELAY = 0.001    # Base sleep duration in seconds.

    for root in range(ROOT_ITERATIONS):
        root_span_name = f"root-operation-{root}"
        with tracer.start_as_current_span(root_span_name):
            _do_work(tracer, DEPTH, BREADTH, BASE_DELAY)


def main():
    tracer = init_tracer()

    # Generate a deeper and wider tree of spans to visualise a complex flame graph.
    generate_complex_workload(tracer)

    # Make sure all spans are flushed before the program exits.
    trace.get_tracer_provider().shutdown()


if __name__ == "__main__":
    main() 