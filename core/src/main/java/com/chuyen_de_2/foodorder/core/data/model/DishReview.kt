package com.chuyen_de_2.foodorder.core.data.model

import com.google.firebase.database.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName

/**
 * Đánh giá món ăn — lưu tại: dish_reviews/{reviewId}
 * Giao diện: Đánh giá món ăn sau khi hoàn tất đơn hàng & Đánh giá tại thực đơn (b5.html & b9.html)
 * Mapping: Bảng 16 - dish_reviews (database_schema.sql)
 */
@IgnoreExtraProperties
data class DishReview(
    @SerializedName("reviewId")
    val reviewId: String = "",

    @SerializedName("orderId")
    val orderId: String = "",

    @SerializedName("dishId")
    val dishId: String = "",

    @SerializedName("dishName")
    val dishName: String = "",

    @SerializedName("storeId")
    val storeId: String = "",

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

    @SerializedName("specText")
    val specText: String = "",

    @SerializedName("avatarColorHex")
    val avatarColorHex: String = "#EE4D2D",

    @SerializedName("likes")
    val likes: Int = 0,

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
