package com.sixteenscreener.app.testing

import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.domain.model.Market

object Exchanges {
    val nyse = Exchange("", "NYSE", "New York Stock Exchange", "XNYS", "USD", "America/New_York", Market.US)
    val xetra = Exchange("DE", "XETRA", "Deutsche Boerse Xetra", "XETR", "EUR", "Europe/Berlin", Market.EU)
    val paris = Exchange("PA", "Euronext Paris", "Euronext Paris", "XPAR", "EUR", "Europe/Paris", Market.EU)
    val tsxVenture = Exchange("V", "TSXV", "TSX Venture Exchange", "XTSX", "CAD", "America/Toronto", null)
    val all = listOf(nyse, xetra, paris, tsxVenture)
}
