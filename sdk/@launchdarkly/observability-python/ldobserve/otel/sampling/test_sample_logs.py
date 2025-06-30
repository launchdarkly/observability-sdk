from opentelemetry.util.types import AnyValue
import pytest
import grpc
from typing import Dict, Optional, Union
from opentelemetry.sdk._logs import LogRecord, LogData  # type: ignore
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import InstrumentationScope
from opentelemetry.trace import SpanContext, TraceFlags
from opentelemetry.trace.span import INVALID_SPAN_ID, INVALID_TRACE_ID
from opentelemetry.sdk._logs.export import LogExportResult  # type: ignore

from ..sampling_log_exporter import (
    sample_logs,
    SamplingLogExporter,
    clone_log_record_with_attributes,
)
from ..sampling import SamplingResult
from ...graph.generated.public_graph_client.get_sampling_config import (
    GetSamplingConfigSampling,
)


def create_log_data(
    severity_text: str,
    message: str,
    attributes: Optional[Dict[str, AnyValue]] = None,
) -> LogData:
    """Create a LogData for testing."""
    log_record = LogRecord(
        body=message,
        attributes=attributes or {},
        severity_text=severity_text,
        resource=Resource.create({}),
        timestamp=0,
        observed_timestamp=0,
        trace_id=INVALID_TRACE_ID,
        span_id=INVALID_SPAN_ID,
        severity_number=None,
    )

    return LogData(
        log_record=log_record, instrumentation_scope=InstrumentationScope("test", "1.0")
    )


class MockSampler:
    """Mock implementation of ExportSampler for testing."""

    def __init__(
        self,
        mock_results: Dict[str, bool],
        enabled: bool = True,
    ):
        self.mock_results = mock_results
        self.enabled = enabled

    def set_config(self, config: Optional[GetSamplingConfigSampling]) -> None:
        pass

    def sample_log(self, record: LogData) -> SamplingResult:
        log_id = f"{record.log_record.severity_text}-{record.log_record.body}"
        should_sample = self.mock_results.get(log_id, True)

        if should_sample:
            return SamplingResult(sample=True, attributes={"samplingRatio": 2})
        else:
            return SamplingResult(sample=False)

    def sample_span(self, span) -> SamplingResult:
        return SamplingResult(sample=True)

    def is_sampling_enabled(self) -> bool:
        return self.enabled


def test_return_all_logs_when_sampling_disabled():
    """Test that all logs are returned when sampling is disabled."""
    mock_sampler = MockSampler({}, False)
    logs = [
        create_log_data("info", "test log 1"),
        create_log_data("error", "test log 2"),
    ]

    sampled_logs = sample_logs(logs, mock_sampler)

    assert len(sampled_logs) == 2
    assert sampled_logs == logs


def test_remove_logs_that_are_not_sampled():
    """Test that logs that are not sampled are removed."""
    mock_sampler = MockSampler(
        {
            "info-test log 1": True,
            "error-test log 2": False,
        }
    )

    logs = [
        create_log_data("info", "test log 1"),
        create_log_data("error", "test log 2"),
    ]

    sampled_logs = sample_logs(logs, mock_sampler)

    assert len(sampled_logs) == 1
    assert sampled_logs[0].log_record.body == "test log 1"
    assert (
        sampled_logs[0].log_record.attributes
        and sampled_logs[0].log_record.attributes["samplingRatio"] == 2
    )


def test_apply_sampling_attributes_to_sampled_logs():
    """Test that sampling attributes are applied to sampled logs."""
    mock_sampler = MockSampler(
        {
            "info-test log 1": True,
            "error-test log 2": True,
        }
    )

    logs = [
        create_log_data("info", "test log 1"),
        create_log_data("error", "test log 2"),
    ]

    sampled_logs = sample_logs(logs, mock_sampler)

    assert len(sampled_logs) == 2
    assert (
        sampled_logs[0].log_record.attributes
        and sampled_logs[0].log_record.attributes["samplingRatio"] == 2
    )
    assert (
        sampled_logs[1].log_record.attributes
        and sampled_logs[1].log_record.attributes["samplingRatio"] == 2
    )


def test_handle_empty_log_array():
    """Test handling of empty log array."""
    mock_sampler = MockSampler({})
    logs: list[LogData] = []

    sampled_logs = sample_logs(logs, mock_sampler)

    assert len(sampled_logs) == 0


def test_handle_logs_with_no_sampling_attributes():
    """Test handling of logs with no sampling attributes."""
    mock_sampler = MockSampler(
        {
            "info-test log 1": True,
        }
    )

    logs = [create_log_data("info", "test log 1")]

    sampled_logs = sample_logs(logs, mock_sampler)

    assert len(sampled_logs) == 1
    assert (
        sampled_logs[0].log_record.attributes
        and sampled_logs[0].log_record.attributes["samplingRatio"] == 2
    )


def test_sample_logs_with_no_attributes():
    """Test sample_logs when sampler returns no attributes."""

    class MockSamplerNoAttributes:
        def is_sampling_enabled(self) -> bool:
            return True

        def sample_log(self, record: LogData) -> SamplingResult:
            return SamplingResult(sample=True, attributes=None)

        def sample_span(self, span) -> SamplingResult:
            return SamplingResult(sample=True)

    mock_sampler = MockSamplerNoAttributes()
    logs = [create_log_data("info", "test log")]

    sampled_logs = sample_logs(logs, mock_sampler)

    assert len(sampled_logs) == 1
    assert sampled_logs[0] == logs[0]  # Should be the original log without modification


def test_clone_log_record_with_attributes():
    """Test clone_log_record_with_attributes function."""
    original_log = create_log_data("info", "test message", {"original": "value"})
    new_attributes: Dict[str, Union[str, int, float, bool]] = {
        "new": "value",
        "another": 123,
    }

    cloned_log = clone_log_record_with_attributes(original_log, new_attributes)

    # Check that it's a new object
    assert cloned_log is not original_log
    assert cloned_log.log_record is not original_log.log_record

    # Check that attributes are merged
    expected_attributes = {"original": "value", "new": "value", "another": 123}
    assert cloned_log.log_record.attributes == expected_attributes

    # Check that other properties are preserved
    assert cloned_log.log_record.body == original_log.log_record.body
    assert cloned_log.log_record.severity_text == original_log.log_record.severity_text
    assert cloned_log.instrumentation_scope == original_log.instrumentation_scope


def test_clone_log_record_with_no_original_attributes():
    """Test clone_log_record_with_attributes when original log has no attributes."""
    original_log = create_log_data("info", "test message", None)
    new_attributes: Dict[str, Union[str, int, float, bool]] = {"new": "value"}

    cloned_log = clone_log_record_with_attributes(original_log, new_attributes)

    assert cloned_log.log_record.attributes == new_attributes


class TestSamplingLogExporter:
    """Test cases for SamplingLogExporter class."""

    def test_init_with_sampler(self):
        """Test SamplingLogExporter initialization."""
        mock_sampler = MockSampler({})
        exporter = SamplingLogExporter(sampler=mock_sampler)

        assert exporter.sampler == mock_sampler

    def test_init_with_all_parameters(self):
        """Test SamplingLogExporter initialization with all parameters."""
        mock_sampler = MockSampler({})

        exporter = SamplingLogExporter(
            sampler=mock_sampler,
            endpoint="http://localhost:4317",
            insecure=True,
            headers={"Authorization": "Bearer token"},
            timeout=30,
        )

        assert exporter.sampler == mock_sampler

    def test_export_with_no_sampled_logs(self):
        """Test export method when no logs are sampled."""
        mock_sampler = MockSampler({"info-test log": False})
        exporter = SamplingLogExporter(sampler=mock_sampler)

        logs = [create_log_data("info", "test log")]

        result = exporter.export(logs)
        assert result == LogExportResult.SUCCESS

    def test_export_with_empty_logs(self):
        """Test export method with empty logs list."""
        mock_sampler = MockSampler({})
        exporter = SamplingLogExporter(sampler=mock_sampler)

        logs = []

        result = exporter.export(logs)
        assert result == LogExportResult.SUCCESS

    def test_export_with_sampled_logs(self, monkeypatch):
        """Test export method when logs are sampled."""
        mock_sampler = MockSampler({"info-test log": True})
        exporter = SamplingLogExporter(sampler=mock_sampler)

        logs = [create_log_data("info", "test log")]

        # Mock the parent class export method
        def mock_parent_export(self, sampled_logs):
            assert len(sampled_logs) == 1
            assert sampled_logs[0].log_record.attributes["samplingRatio"] == 2
            return LogExportResult.SUCCESS

        # Use monkeypatch to replace the parent class method
        monkeypatch.setattr(
            exporter.__class__.__bases__[0], "export", mock_parent_export
        )

        result = exporter.export(logs)
        assert result == LogExportResult.SUCCESS
