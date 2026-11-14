package com.sixteenscreener.adapter.outbound.secedgar

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure

@Serializable
data class SecTickerDto(
    @SerialName("cik_str") val cik: Long,
    val ticker: String,
)

@Serializable
data class SecCompanyFactsDto(
    val facts: SecFactsDto = SecFactsDto(),
)

@Serializable
data class SecFactsDto(
    @SerialName("us-gaap")
    @Serializable(with = BalanceSheetConceptsSerializer::class)
    val usGaap: Map<String, SecConceptDto> = emptyMap(),
)

@Serializable
data class SecConceptDto(
    val label: String? = null,
    val units: Map<String, List<SecFactDto>> = emptyMap(),
)

@Serializable
data class SecFactDto(
    val start: String? = null,
    val end: String,
    @SerialName("val") val value: Double,
    val accn: String,
    val fy: Int? = null,
    val fp: String? = null,
    val form: String,
    val filed: String,
)

object BalanceSheetConceptsSerializer : KSerializer<Map<String, SecConceptDto>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SecBalanceSheetConcepts") {
        SecBalanceSheetConcepts.ALL.forEach { concept ->
            element(concept, SecConceptDto.serializer().descriptor, isOptional = true)
        }
    }

    override fun deserialize(decoder: Decoder): Map<String, SecConceptDto> =
        decoder.decodeStructure(descriptor) {
            buildMap {
                while (true) {
                    val index = decodeElementIndex(descriptor)
                    if (index == CompositeDecoder.DECODE_DONE) break
                    put(descriptor.getElementName(index), decodeSerializableElement(descriptor, index, SecConceptDto.serializer()))
                }
            }
        }

    override fun serialize(encoder: Encoder, value: Map<String, SecConceptDto>) =
        throw UnsupportedOperationException("SEC company facts are only decoded")
}
