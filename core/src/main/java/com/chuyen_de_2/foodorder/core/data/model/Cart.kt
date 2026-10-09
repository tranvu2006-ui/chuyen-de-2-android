package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Giỏ hàng — lưu tại: carts/{userId}/{shopId}
 * Firebase Realtime Database JSON Schema (Section 4 - doc-adr)
 */
data class Cart(
    @SerializedName("shopId")
    val shopId: String = "",

    @SerializedName("shopName")
    val shopName: String = "",

    @SerializedName("totalAmount")
    val totalAmount: Long = 0,

    @SerializedName("items")
    val items: Map<String, CartItem> = emptyMap()
)

/**
 * Payload được sử dụng bởi thuật toán Debounce trong CartViewModel
 * (Section 6 - doc-adr: Core Algorithm)
 */
data class CartPayload(
    val userId: String,
    val shopId: String,
    val cart: Cart
)
