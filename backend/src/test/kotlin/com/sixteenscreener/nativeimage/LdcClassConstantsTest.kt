package com.sixteenscreener.nativeimage

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly

private val MARKER = LdcMarker::class.java.name

private fun classFileOf(type: Class<*>): ByteArray =
    requireNotNull(type.getResourceAsStream("${type.simpleName}.class")) { "${type.name} class file not found" }
        .use { it.readBytes() }

private fun ldcClassNamesOf(type: Class<*>) = LdcClassConstants.binaryNamesIn(classFileOf(type))

class LdcClassConstantsTest : FunSpec({
    test("finds a class constant loaded by ldc") {
        ldcClassNamesOf(LdcFixture::class.java) shouldContainExactly setOf(MARKER)
    }

    test("finds a class constant loaded by ldc_w from a constant pool index above 255") {
        ldcClassNamesOf(LdcWideIndexFixture::class.java) shouldContainExactly setOf(MARKER)
    }

    test("walks past a tableswitch to the ldc after it") {
        ldcClassNamesOf(TableSwitchFixture::class.java) shouldContainExactly setOf(MARKER)
    }

    test("walks past a lookupswitch to the ldc after it") {
        ldcClassNamesOf(LookupSwitchFixture::class.java) shouldContainExactly setOf(MARKER)
    }

    test("keeps constant pool indices aligned across Long and Double constants") {
        ldcClassNamesOf(WideConstantsFixture::class.java) shouldContainExactly setOf(MARKER)
    }

    test("skips array class constants") {
        ldcClassNamesOf(ArrayClassFixture::class.java) shouldContainExactly setOf(MARKER)
    }

    test("finds nothing in a class without class constants") {
        ldcClassNamesOf(LdcMarker::class.java).shouldBeEmpty()
    }
})
