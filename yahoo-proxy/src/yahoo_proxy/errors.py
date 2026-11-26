"""Failures of Yahoo calls, as values."""

from dataclasses import dataclass
from enum import StrEnum


class YahooErrorKind(StrEnum):
    """Reason why Yahoo data could not be returned."""

    NOT_FOUND = "NOT_FOUND"
    RATE_LIMITED = "RATE_LIMITED"
    UPSTREAM_FAILURE = "UPSTREAM_FAILURE"
    TIMEOUT = "TIMEOUT"


@dataclass(frozen=True, slots=True)
class YahooError:
    """A failed Yahoo call with a message for the caller and the logs."""

    kind: YahooErrorKind
    message: str
