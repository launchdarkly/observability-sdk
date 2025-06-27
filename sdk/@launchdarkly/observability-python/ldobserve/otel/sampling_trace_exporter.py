from typing import List, Optional, Dict, Callable, Union
from opentelemetry.sdk.trace.export import SpanExporter, SpanExportResult
from opentelemetry.sdk.trace import ReadableSpan
from opentelemetry.trace import SpanContext
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from typing import Protocol
from copy import deepcopy

from .sampling import CustomSampler, SamplingResult, default_sampler
from ..graph.generated.public_graph_client.get_sampling_config import (
    GetSamplingConfigSampling,
)


class ExportSampler(Protocol):
    """Protocol for export samplers."""
    
    def sample_span(self, span: ReadableSpan) -> SamplingResult:
        """Sample a span and return the result."""
        ...
    
    def is_sampling_enabled(self) -> bool:
        """Return True if sampling is enabled."""
        ...
    
    def set_config(self, config: Optional[GetSamplingConfigSampling]) -> None:
        """Set the sampling configuration."""
        ...


def clone_readable_span_with_attributes(
    span: ReadableSpan,
    attributes: Dict[str, Union[str, int, float, bool]],
) -> ReadableSpan:
    """Clone a ReadableSpan with merged attributes."""
    # Create a new span with merged attributes
    merged_attributes = dict(span.attributes or {})
    merged_attributes.update(attributes)
    
    return ReadableSpan(
        name=span.name,
        context=span.get_span_context(),
        parent=span.parent,
        resource=span.resource,
        instrumentation_scope=span.instrumentation_scope,
        attributes=merged_attributes,
        events=span.events,
        links=span.links,
        status=span.status,
        start_time=span.start_time,
        end_time=span.end_time,
        kind=span.kind
    )

def sample_spans(
    items: List[ReadableSpan],
    sampler: ExportSampler,
) -> List[ReadableSpan]:
    """Sample spans based on the sampler configuration."""
    if not sampler.is_sampling_enabled():
        return items
    
    omitted_span_ids: List[int] = []
    span_by_id: Dict[int, ReadableSpan] = {}
    children_by_parent_id: Dict[int, List[int]] = {}
    
    # First pass: sample items and build parent-child relationships
    for item in items:
        span_context = item.get_span_context()
        if span_context is None:
            continue
            
        # Try to get parent span ID - this might not be available on all span types
        parent_span_id = item.parent.span_id if item.parent else None
        if parent_span_id:
            if parent_span_id not in children_by_parent_id:
                children_by_parent_id[parent_span_id] = []
            children_by_parent_id[parent_span_id].append(
                span_context.span_id
            )
        
        sample_result = sampler.sample_span(item)
        if sample_result.sample:
            if sample_result.attributes:
                span_by_id[span_context.span_id] = clone_readable_span_with_attributes(
                    item, sample_result.attributes
                )
            else:
                span_by_id[span_context.span_id] = item
        else:
            omitted_span_ids.append(span_context.span_id)
    
    # Remove children of spans that have been sampled out
    while omitted_span_ids:
        span_id = omitted_span_ids.pop(0)
        affected_spans = children_by_parent_id.get(span_id)
        if not affected_spans:
            continue
        
        for span_id_to_remove in affected_spans:
            if span_id_to_remove in span_by_id:
                del span_by_id[span_id_to_remove]
            omitted_span_ids.append(span_id_to_remove)
    
    return list(span_by_id.values())


class SamplingTraceExporter(OTLPSpanExporter):
    """Trace exporter that applies sampling before exporting."""
    
    def __init__(
        self,
        config: Optional[Dict[str, object]] = None,
        sampler: Optional[CustomSampler] = None,
    ):
        super().__init__(**(config or {}))  # type: ignore
        self.sampler = sampler or CustomSampler()
    
    def export(self, spans: List[ReadableSpan]) -> SpanExportResult:
        """Export spans with sampling applied."""
        sampled_spans = sample_spans(spans, self.sampler)
        if not sampled_spans:
            return SpanExportResult.SUCCESS
        
        return super().export(sampled_spans)
