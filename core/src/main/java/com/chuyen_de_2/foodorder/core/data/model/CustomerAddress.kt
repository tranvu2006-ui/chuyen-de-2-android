package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Địa chỉ giao hàng — lưu tại: customer_addresses/{addressId}
 * Giao diện: khách/b8.html — Checkout chọn địa chỉ TP.HCM
 * Mapping: Bảng 3 - customer_addresses (database_schema.sql)
 */
data class CustomerAddress(
    @SerializedName("addressId")
    val addressId: String = "",

    @SerializedName("customerId")
    val customerId: String = "",

    @SerializedName("receiverName")
    val receiverName: String = "",

    @SerializedName("receiverPhone")
    val receiverPhone: String = "",

    @SerializedName("streetAddress", alternate = ["address"])
    val streetAddress: String = "",

    @SerializedName("latitude")
    val latitude: Double = 0.0,

    @SerializedName("longitude")
    val longitude: Double = 0.0,

    @SerializedName("isDefault")
    val isDefault: Boolean = false
) {
    /** Trả về địa chỉ đầy đủ dạng hiển thị */
    fun fullAddress(): String = streetAddress
}
