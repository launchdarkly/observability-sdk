from typing import List, Optional, Dict, Union, Sequence, Tuple
from opentelemetry.sdk._logs import LogRecord  # type: ignore
from opentelemetry.sdk._logs.export import LogExporter, LogExportResult  # type: ignore
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter  # type: ignore
from typing import Protocol
import grpc

from .sampling import CustomSampler, SamplingResult
from ..graph.generated.public_graph_client.get_sampling_config import (
    GetSamplingConfigSampling,
)


class ExportSampler(Protocol):
    """Protocol for export samplers."""
    
    def sample_log(self, record: LogRecord) -> SamplingResult:
        """Sample a log and return the result."""
        ...
    
    def is_sampling_enabled(self) -> bool:
        """Return True if sampling is enabled."""
        ...
    
    def set_config(self, config: Optional[GetSamplingConfigSampling]) -> None:
        """Set the sampling configuration."""
        ...


def clone_log_record_with_attributes(
    log: LogRecord,
    attributes: Dict[str, Union[str, int, float, bool]],
) -> LogRecord:
    """Clone a LogRecord with merged attributes."""
    # Create a new log record with merged attributes
    merged_attributes = dict(log.attributes or {})
    merged_attributes.update(attributes)
    
    return LogRecord(
        body=log.body,
        attributes=merged_attributes,
        severity_text=log.severity_text,
        resource=log.resource,
        timestamp=log.timestamp,
        observed_timestamp=log.observed_timestamp,
        trace_id=log.trace_id,
        span_id=log.span_id,
        trace_flags=log.trace_flags,
        severity_number=log.severity_number
    )


def sample_logs(
    items: List[LogRecord],
    sampler: ExportSampler,
) -> List[LogRecord]:
    """Sample logs based on the sampler configuration."""
    if not sampler.is_sampling_enabled():
        return items
    
    sampled_logs: List[LogRecord] = []
    
    for item in items:
        sample_result = sampler.sample_log(item)
        if sample_result.sample:
            if sample_result.attributes:
                sampled_logs.append(
                    clone_log_record_with_attributes(item, sample_result.attributes)
                )
            else:
                sampled_logs.append(item)
        # If not sampled, we simply don't include it in the result
    
    return sampled_logs


class SamplingLogExporter(OTLPLogExporter):
    """Log exporter that applies sampling before exporting."""
    
    def __init__(
        self,
        endpoint: Optional[str] = None,
        insecure: Optional[bool] = None,
        credentials: Optional[grpc.ChannelCredentials] = None,
        headers: Optional[Union[Sequence[Tuple[str, str]], Dict[str, str], str]] = None,
        timeout: Optional[int] = None,
        compression: Optional[grpc.Compression] = None,
        sampler: Optional[CustomSampler] = None,
    ):
        super().__init__(
            endpoint=endpoint,
            insecure=insecure,
            credentials=credentials,
            headers=headers,
            timeout=timeout,
            compression=compression,
        )
        self.sampler = sampler or CustomSampler()
    
    def export(self, logs: List[LogRecord]) -> LogExportResult:
        """Export logs with sampling applied."""
        sampled_logs = sample_logs(logs, self.sampler)
        if not sampled_logs:
            return LogExportResult.SUCCESS
        
        return super().export(sampled_logs)  # type: ignore 