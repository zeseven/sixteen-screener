package com.sixteenscreener.contract

import com.sixteenscreener.port.outbound.SecCikRepository
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

fun secCikRepositoryContract(newRepository: suspend () -> SecCikRepository) = funSpec {
    val storedAt = Instant.parse("2026-11-14T12:00:00Z")
    val ttl = Duration.ofDays(14)

    test("no CIK stored") {
        newRepository().find("AAPL", storedAt).shouldBeNull()
    }

    test("a stored CIK is found per ticker until it expires 14 days after it was stored") {
        val repository = newRepository()
        repository.save("BRK-B", 1067983, storedAt)

        repository.find("BRK-B", storedAt) shouldBe 1067983L
        repository.find("BRK-B", storedAt.plus(ttl).minusMillis(1)) shouldBe 1067983L
        repository.find("BRK-B", storedAt.plus(ttl)).shouldBeNull()
        repository.find("BRK-A", storedAt).shouldBeNull()
    }

    test("saving again replaces the CIK and extends the expiry") {
        val repository = newRepository()
        repository.save("AAPL", 1, storedAt)
        repository.save("AAPL", 320193, storedAt.plus(Duration.ofDays(10)))

        repository.find("AAPL", storedAt.plus(Duration.ofDays(20))) shouldBe 320193L
    }
}
