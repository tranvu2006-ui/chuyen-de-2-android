package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Thông báo ứng dụng — lưu tại: notifications/{notificationId}
 * Giao diện: khách/b10.html — Hộp thư thông báo
 * Mapping: Bảng 15 - notifications (database_schema.sql)
 */
data class Notification(
    @SerializedName("notificationId")
    val notificationId: String = "",

    @SerializedName("userId")
    val userId: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("content")
    val content: String = "",

    @SerializedName("notificationType")
    val notificationType: String = "",

    @SerializedName("isRead")
    val isRead: Boolean = false,

    @SerializedName("createdAt")
    val createdAt: Long = 0
) {
    object Type {
        const val ORDER_UPDATE = "ORDER_UPDATE"
        const val TRUST_SCORE = "TRUST_SCORE"
        const val WARNING = "WARNING"
        const val SYSTEM = "SYSTEM"
    }
}
