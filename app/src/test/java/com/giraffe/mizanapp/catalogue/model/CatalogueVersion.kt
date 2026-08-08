package com.giraffe.mizanapp.catalogue.model

import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Fixes the whole set of task versions at a point in time.
 *
 * [effectiveFrom] is what makes "which version applied on this date" answerable
 * for a day the user never opened. Without it, backfilling a skipped day would
 * have to guess.
 */
@Serializable
data class CatalogueVersion(
    val version: Int,
    @Serializable(with = LocalDateSerializer::class)
    val effectiveFrom: LocalDate,
)

object LocalDateSerializer : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}
