import pandas as pd

from yfinance import exceptions as exceptions

class _DebugConfig:
    hide_exceptions: bool

class _NetworkConfig:
    retries: int

class _Config:
    debug: _DebugConfig
    network: _NetworkConfig

config: _Config
__version__: str

def set_tz_cache_location(cache_dir: str) -> None: ...

class Ticker:
    def __init__(self, ticker: str) -> None: ...
    @property
    def info(self) -> dict[str, object]: ...
    @property
    def balance_sheet(self) -> pd.DataFrame: ...
    def history(self, *, period: str, interval: str, auto_adjust: bool) -> pd.DataFrame: ...
    def get_history_metadata(self) -> dict[str, object]: ...
    def get_dividends(self, period: str = ...) -> pd.Series[float]: ...
