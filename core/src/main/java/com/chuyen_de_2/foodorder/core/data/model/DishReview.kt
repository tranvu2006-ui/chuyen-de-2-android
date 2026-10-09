package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Đánh giá món ăn — lưu tại: dish_reviews/{reviewId}
 * Giao diện: Đánh giá món ăn sau khi hoàn tất đơn hàng
 * Mapping: Bảng 16 - dish_reviews (database_schema.sql)
 */
data class DishReview(
    @SerializedName("reviewId")
    val reviewId: String = "",

    @SerializedName("orderId")
    val orderId: String = "",

    @SerializedName("dishId")
    val dishId: String = "",

    @SerializedName("customerId")
    val customerId: String = "",

    @SerializedName("customerName")
    val customerName: String = "",

    @SerializedName("rating")
    val rating: Int = 5,

    @SerializedName("comment")
    val comment: String = "",

    @SerializedName("imageUrl")
    val imageUrl: String = "",

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
