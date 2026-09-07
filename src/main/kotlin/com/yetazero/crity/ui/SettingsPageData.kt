package com.yetazero.crity.ui

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec

internal class SettingsPageData {
    var action: String? = null
    var path: String? = null
    var epoch: String? = null
    var source: String? = null
    var text: String? = null
    var number: Double? = null
    var flag: Boolean? = null

    companion object {
        val CODEC: BuilderCodec<SettingsPageData> = BuilderCodec.builder(SettingsPageData::class.java, ::SettingsPageData)
            .append(KeyedCodec("Action", Codec.STRING), { data, value -> data.action = value }, { it.action }).add()
            .append(KeyedCodec("Path", Codec.STRING), { data, value -> data.path = value }, { it.path }).add()
            .append(KeyedCodec("Epoch", Codec.STRING), { data, value -> data.epoch = value }, { it.epoch }).add()
            .append(KeyedCodec("@Text", Codec.STRING), { data, value -> data.text = value }, { it.text }).add()
            .append(KeyedCodec("@Number", Codec.DOUBLE), { data, value -> data.number = value }, { it.number }).add()
            .append(KeyedCodec("@Flag", Codec.BOOLEAN), { data, value -> data.flag = value }, { it.flag }).add()
            .append(KeyedCodec("Source", Codec.STRING), { data, value -> data.source = value }, { it.source }).add()
            .build()
    }

    fun value(): String = text ?: flag?.toString() ?: number?.let {
        require(it.isFinite()) { "Enter a finite number." }
        java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()
    } ?: throw IllegalArgumentException("A value is required.")
}
