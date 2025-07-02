from typing import Optional
from opentelemetry.instrumentation import auto_instrumentation
import ldobserve.distro
from ldobserve.config import ObservabilityConfig


def init(project_id: str, config: Optional[ObservabilityConfig] = None):
    ldobserve.distro.init(project_id, config)
    auto_instrumentation.initialize()
