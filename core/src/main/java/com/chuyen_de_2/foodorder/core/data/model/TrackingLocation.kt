package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Tọa độ GPS Realtime — lưu tại: tracking/{orderId}
 * Firebase Realtime Database JSON Schema (Section 4 - doc-adr)
 *
 * Shipper cập nhật liên tục qua Foreground Service.
 * Khách hàng lắng nghe qua addValueEventListener.
 */
data class TrackingLocation(
    @SerializedName("lat")
    val lat: Double = 0.0,

    @SerializedName("lng")
    val lng: Double = 0.0,

    @SerializedName("updatedAt")
    val updatedAt: Long = 0
)
