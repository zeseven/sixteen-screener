"""Conversion of Yahoo minor currency units into main units."""

from collections.abc import Mapping
from dataclasses import dataclass
from typing import Final


@dataclass(frozen=True, slots=True)
class CurrencyUnit:
    """A currency code in its main unit and the divisor that converts amounts of the original unit into it."""

    code: str
    divisor: int

    def to_main_unit(self, amount: float) -> float:
        """Converts an amount of the original unit into the main unit."""
        return amount / self.divisor


_MINOR_UNITS: Final[Mapping[str, CurrencyUnit]] = {
    "GBp": CurrencyUnit("GBP", 100),
    "GBX": CurrencyUnit("GBP", 100),
    "ZAc": CurrencyUnit("ZAR", 100),
    "ZAC": CurrencyUnit("ZAR", 100),
    "ILA": CurrencyUnit("ILS", 100),
}


def currency_unit(yahoo_currency: str) -> CurrencyUnit:
    """Returns the main unit of a Yahoo currency code; codes that are not minor units are returned unchanged."""
    return _MINOR_UNITS.get(yahoo_currency, CurrencyUnit(yahoo_currency, 1))
