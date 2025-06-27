from typing import List, Optional, Dict, Callable, Union
from opentelemetry.sdk.trace.export import SpanExporter, SpanExportResult
from opentelemetry.sdk.trace import ReadableSpan
from opentelemetry.trace import SpanContext
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
import random
import re
from typing import Protocol

from .sampling import CustomSampler, SamplingResult, default_sampler
from ..graph.generated.public_graph_client.get_sampling_config import (
    GetSamplingConfigSampling,
    GetSamplingConfigSamplingSpans,
    GetSamplingConfigSamplingSpansAttributes,
    GetSamplingConfigSamplingSpansEvents,
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


def sample_spans(
    items: List[ReadableSpan],
    sampler: ExportSampler,
) -> List[ReadableSpan]:
    """Sample spans based on the sampler configuration."""
    if not sampler.is_sampling_enabled():
        return items
    
    omitted_span_ids: List[str] = []
    span_by_id: Dict[str, ReadableSpan] = {}
    children_by_parent_id: Dict[str, List[str]] = {}
    
    # First pass: sample items and build parent-child relationships
    for item in items:
        span_context = item.get_span_context()
        if span_context is None:
            continue
            
        # Try to get parent span ID - this might not be available on all span types
        parent_span_id = None
        try:
            parent_span_id = getattr(item, 'parent_span_id', None)
        except AttributeError:
            pass
        
        if parent_span_id:
            if parent_span_id not in children_by_parent_id:
                children_by_parent_id[parent_span_id] = []
            children_by_parent_id[parent_span_id].append(
                str(span_context.span_id)
            )
        
        sample_result = sampler.sample_span(item)
        if sample_result.sample:
            span_by_id[str(span_context.span_id)] = item
        else:
            omitted_span_ids.append(str(span_context.span_id))
    
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
