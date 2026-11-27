package com.sixteenscreener.app.testing

import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.domain.model.Market

object Exchanges {
    val nyse = Exchange("", "NYSE", "New York Stock Exchange", "XNYS", "US", "", "US", true, "USD", "America/New_York", Market.US)
    val xetra = Exchange("DE", "XETRA", "Deutsche Boerse Xetra", "XETR", "XETRA", "DE", "DE", true, "EUR", "Europe/Berlin", Market.EU)
    val paris = Exchange("PA", "Paris", "Euronext Paris", "XPAR", "PA", "PA", "FR", true, "EUR", "Europe/Paris", Market.EU)
    val tsxVenture = Exchange("V", "TSXV", "TSX Venture Exchange", "XTSX", "V", "V", "CA", true, "CAD", "America/Toronto", null)
    val stuttgart = Exchange("SG", "STU", "Boerse Stuttgart", "XSTU", "STU", null, "DE", false, "EUR", "Europe/Berlin", Market.EU)
    val all = listOf(nyse, xetra, paris, tsxVenture)
}
