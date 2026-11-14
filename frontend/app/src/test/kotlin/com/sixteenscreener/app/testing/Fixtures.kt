package com.sixteenscreener.app.testing

object Fixtures {
    fun read(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "Fixture $name not found" }.readText()
}
