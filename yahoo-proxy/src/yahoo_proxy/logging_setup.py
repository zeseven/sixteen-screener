"""Logs as one JSON object per line, in the structured format of Cloud Logging."""

import logging
import sys

import msgspec


class CloudLoggingFormatter(logging.Formatter):
    """Formats a record as `{"severity", "message", "logger"}`; a traceback is appended to the message."""

    def format(self, record: logging.LogRecord) -> str:
        """Formats one record."""
        message = record.getMessage()
        if record.exc_info is not None:
            message = f"{message}\n{self.formatException(record.exc_info)}"
        entry = {"severity": record.levelname, "message": message, "logger": record.name}
        return msgspec.json.encode(entry).decode()


def configure_logging(level: int = logging.INFO) -> None:
    """Sends all logs, including uvicorn's, to stdout in the Cloud Logging format."""
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(CloudLoggingFormatter())
    logging.basicConfig(level=level, handlers=[handler], force=True)
