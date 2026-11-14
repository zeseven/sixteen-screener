package com.sixteenscreener

object Fixtures {
    fun text(path: String): String =
        checkNotNull(Fixtures::class.java.getResource("/fixtures/$path")) { "Fixture '$path' not found" }.readText()
}
