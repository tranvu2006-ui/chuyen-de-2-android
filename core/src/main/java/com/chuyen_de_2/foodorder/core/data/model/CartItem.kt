package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Mục trong giỏ hàng — lưu tại: carts/{userId}/{shopId}/items/{itemId}
 * Firebase Realtime Database JSON Schema (Section 4 - doc-adr)
 */
data class CartItem(
    @SerializedName("itemId")
    val itemId: String = "",

    @SerializedName("name")
    val name: String = "",

    @SerializedName("price")
    val price: Long = 0,

    @SerializedName("quantity")
    val quantity: Int = 0,

    @SerializedName("imageUrl")
    val imageUrl: String = "",

    @SerializedName("options")
    val options: String = ""
)
