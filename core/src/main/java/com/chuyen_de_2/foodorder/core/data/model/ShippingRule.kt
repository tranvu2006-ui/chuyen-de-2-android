package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Cấu hình phí ship TP.HCM
 * Giao diện: khách/b8.html — Phân vùng cước ship
 * Mapping: Bảng 11 - shipping_rules (database_schema.sql)
 */
data class ShippingRule(
    @SerializedName("ruleId")
    val ruleId: String = "",

    @SerializedName("zoneType")
    val zoneType: String = "",

    @SerializedName("zoneName")
    val zoneName: String = "",

    @SerializedName("feeAmount")
    val feeAmount: Long = 0,

    @SerializedName("maxDistanceKm")
    val maxDistanceKm: Double = 0.0,

    @SerializedName("isActive")
    val isActive: Boolean = true
) {
    /** Loại khu vực phí ship */
    object ZoneType {
        const val INTRA_DISTRICT = "INTRA_DISTRICT"     // 15,000đ
        const val INTER_DISTRICT = "INTER_DISTRICT"     // 25,000đ
        const val SUBURBAN = "SUBURBAN"                   // 45,000đ
    }
}
