import logging
from typing import Optional
from opentelemetry.instrumentation import auto_instrumentation
from ldobserve.config import ObservabilityConfig
from ldobserve.observe import _ObserveInstance
from ldobserve.config import _ProcessedConfig
import ldobserve.observe
from ldobserve.otel.configuration import _OTELConfiguration


def init(project_id: str, config: Optional[ObservabilityConfig] = None):
    processed_config = _ProcessedConfig(config or ObservabilityConfig())
    logging.getLogger(__name__).info("Processed config: %s", processed_config)
    otel_configuration = _OTELConfiguration(project_id, processed_config)
    auto_instrumentation.initialize()
    ldobserve.observe._instance = _ObserveInstance(project_id, otel_configuration)
