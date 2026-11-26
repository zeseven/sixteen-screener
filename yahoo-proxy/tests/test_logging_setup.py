import logging
import sys

import msgspec

from yahoo_proxy.logging_setup import CloudLoggingFormatter


class LogEntry(msgspec.Struct):
    severity: str
    message: str
    logger: str


def format_record(record: logging.LogRecord) -> LogEntry:
    return msgspec.json.decode(CloudLoggingFormatter().format(record), type=LogEntry)


def test_formats_record_as_cloud_logging_json() -> None:
    record = logging.LogRecord(
        "yahoo_proxy.api", logging.WARNING, __file__, 1, "Yahoo failed for %s", ("SAP.DE",), None
    )

    assert format_record(record) == LogEntry(
        severity="WARNING", message="Yahoo failed for SAP.DE", logger="yahoo_proxy.api"
    )


def test_appends_traceback_to_message() -> None:
    try:
        raise KeyError("price")
    except KeyError:
        record = logging.LogRecord("yahoo_proxy", logging.ERROR, __file__, 1, "Unexpected", (), sys.exc_info())

    entry = format_record(record)

    assert entry.severity == "ERROR"
    assert entry.message.startswith("Unexpected\nTraceback")
    assert "KeyError: 'price'" in entry.message
