package com.sixteenscreener

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NettyAllocatorTest : FunSpec({
    val property = "io.grpc.netty.shaded.io.netty.allocator.type"

    fun withProperty(value: String?, block: () -> Unit) {
        val original = System.getProperty(property)
        try {
            if (value == null) System.clearProperty(property) else System.setProperty(property, value)
            block()
        } finally {
            if (original == null) System.clearProperty(property) else System.setProperty(property, original)
        }
    }

    test("selects the pooled allocator when none is configured") {
        withProperty(null) {
            usePooledNettyAllocator()
            System.getProperty(property) shouldBe "pooled"
        }
    }

    test("keeps an explicitly configured allocator") {
        withProperty("unpooled") {
            usePooledNettyAllocator()
            System.getProperty(property) shouldBe "unpooled"
        }
    }
})
