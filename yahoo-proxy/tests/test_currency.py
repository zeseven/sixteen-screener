import pytest

from yahoo_proxy.currency import CurrencyUnit, currency_unit


@pytest.mark.parametrize(
    ("yahoo_currency", "expected"),
    [
        ("GBp", CurrencyUnit("GBP", 100)),
        ("GBX", CurrencyUnit("GBP", 100)),
        ("ZAc", CurrencyUnit("ZAR", 100)),
        ("ZAC", CurrencyUnit("ZAR", 100)),
        ("ILA", CurrencyUnit("ILS", 100)),
        ("EUR", CurrencyUnit("EUR", 1)),
        ("GBP", CurrencyUnit("GBP", 1)),
        ("USD", CurrencyUnit("USD", 1)),
    ],
)
def test_currency_unit_maps_minor_units_to_main_units(yahoo_currency: str, expected: CurrencyUnit) -> None:
    assert currency_unit(yahoo_currency) == expected


def test_to_main_unit_divides_minor_unit_amounts() -> None:
    assert currency_unit("GBp").to_main_unit(3615.5) == pytest.approx(36.155)


def test_to_main_unit_keeps_main_unit_amounts() -> None:
    assert currency_unit("EUR").to_main_unit(193.24) == 193.24
