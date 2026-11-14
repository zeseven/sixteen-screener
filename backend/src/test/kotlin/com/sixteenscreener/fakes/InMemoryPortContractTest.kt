package com.sixteenscreener.fakes

import com.sixteenscreener.contract.CompanyCacheUnderTest
import com.sixteenscreener.contract.companyCacheContract
import com.sixteenscreener.contract.exchangeDirectoryStoreContract
import com.sixteenscreener.contract.logoMissRepositoryContract
import com.sixteenscreener.contract.logoStorageContract
import com.sixteenscreener.contract.profileRepositoryContract
import com.sixteenscreener.contract.requestCounterRepositoryContract
import com.sixteenscreener.contract.secCikRepositoryContract
import com.sixteenscreener.contract.sourceCallOutcomeRepositoryContract
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import io.kotest.core.spec.style.FunSpec

class InMemoryProfileRepositoryTest : FunSpec({
    include(profileRepositoryContract { InMemoryProfileRepository() })
})

class InMemoryCompanyCacheTest : FunSpec({
    include(
        companyCacheContract {
            object : CompanyCacheUnderTest {
                override val cache = InMemoryCompanyCache()
                override suspend fun history(source: SourceId, symbol: Symbol) = cache.history(source, symbol)
            }
        },
    )
})

class InMemoryRequestCounterRepositoryTest : FunSpec({
    include(requestCounterRepositoryContract { InMemoryRequestCounterRepository() })
})

class InMemorySourceCallOutcomeRepositoryTest : FunSpec({
    include(sourceCallOutcomeRepositoryContract { InMemorySourceCallOutcomeRepository() })
})

class InMemoryLogoMissRepositoryTest : FunSpec({
    include(logoMissRepositoryContract { InMemoryLogoMissRepository() })
})

class InMemoryExchangeDirectoryStoreTest : FunSpec({
    include(exchangeDirectoryStoreContract { InMemoryExchangeDirectoryStore() })
})

class InMemoryLogoStorageTest : FunSpec({
    include(logoStorageContract { InMemoryLogoStorage() })
})

class InMemorySecCikRepositoryTest : FunSpec({
    include(secCikRepositoryContract { InMemorySecCikRepository() })
})
