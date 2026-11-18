package com.sixteenscreener.app.testing

import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.domain.model.Market

object Exchanges {
    val nyse = Exchange("", "NYSE", "New York Stock Exchange", "XNYS", "US", true, "USD", "America/New_York", Market.US)
    val xetra = Exchange("DE", "XETRA", "Deutsche Boerse Xetra", "XETR", "DE", true, "EUR", "Europe/Berlin", Market.EU)
    val paris = Exchange("PA", "Paris", "Euronext Paris", "XPAR", "FR", true, "EUR", "Europe/Paris", Market.EU)
    val tsxVenture = Exchange("V", "TSXV", "TSX Venture Exchange", "XTSX", "CA", true, "CAD", "America/Toronto", null)
    val stuttgart = Exchange("SG", "STU", "Boerse Stuttgart", "XSTU", "DE", false, "EUR", "Europe/Berlin", Market.EU)
    val all = listOf(nyse, xetra, paris, tsxVenture)
}
