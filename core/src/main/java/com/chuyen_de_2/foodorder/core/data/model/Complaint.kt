package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Khiếu nại đơn hàng — lưu tại: complaints/{complaintId}
 * Giao diện: khách/b12.html — Gửi khiếu nại
 *            admin.html — Xử lý khiếu nại 3 mức phạt
 * Mapping: Bảng 14 - complaints (database_schema.sql)
 */
data class Complaint(
    @SerializedName("complaintId")
    val complaintId: String = "",

    @SerializedName("orderId")
    val orderId: String = "",

    @SerializedName("customerId")
    val customerId: String = "",

    @SerializedName("storeId")
    val storeId: String = "",

    @SerializedName("reason")
    val reason: String = "",

    @SerializedName("evidenceImage")
    val evidenceImage: String = "",

    @SerializedName("status")
    val status: String = ComplaintStatus.PENDING,

    @SerializedName("penaltyApplied")
    val penaltyApplied: String = "NONE",

    @SerializedName("adminNote")
    val adminNote: String = "",

    @SerializedName("createdAt")
    val createdAt: Long = 0
) {
    object ComplaintStatus {
        const val PENDING = "PENDING"
        const val RESOLVED = "RESOLVED"
        const val REJECTED = "REJECTED"
    }
}
