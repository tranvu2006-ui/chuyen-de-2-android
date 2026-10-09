package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Bảng 21: trust_score_logs — Lịch sử biến động điểm uy tín khách hàng
 * Lưu tại: trust_score_logs/{logId}
 */
data class TrustScoreLog(
    @SerializedName("logId")
    val logId: String = "",

    @SerializedName("customerId")
    val customerId: String = "",

    @SerializedName("orderId")
    val orderId: String? = null,

    @SerializedName("changeAmount")
    val changeAmount: Int = 0,

    @SerializedName("oldScore")
    val oldScore: Int = 0,

    @SerializedName("newScore")
    val newScore: Int = 0,

    @SerializedName("reason")
    val reason: String = "",

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
