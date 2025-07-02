from dataclasses import dataclass
import typing


@dataclass(kw_only=True)
class RequestContext:
    """Request context containing session and request identifiers."""

    session_id: typing.Optional[str] = None
    request_id: typing.Optional[str] = None
