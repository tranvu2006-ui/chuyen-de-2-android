package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Bảng 6: merchants — Hồ sơ chủ cửa hàng / đối tác
 * Lưu tại: merchants/{merchantId}
 * Chứa hồ sơ định danh, 4 ảnh CCCD, giấy phép kinh doanh để Admin xét duyệt
 */
data class Merchant(
    @SerializedName("merchantId")
    val merchantId: String = "",

    @SerializedName("cccdNumber")
    val cccdNumber: String = "",

    @SerializedName("cccdFrontUrl")
    val cccdFrontUrl: String = "",

    @SerializedName("cccdBackUrl")
    val cccdBackUrl: String = "",

    @SerializedName("cccdHoldUrl")
    val cccdHoldUrl: String = "",

    @SerializedName("cccdPortraitUrl")
    val cccdPortraitUrl: String = "",

    @SerializedName("businessLicense")
    val businessLicense: String = "",

    @SerializedName("taxCode")
    val taxCode: String = "",

    @SerializedName("bankName")
    val bankName: String = "",

    @SerializedName("bankAccount")
    val bankAccount: String = "",

    @SerializedName("approvalStatusId")
    val approvalStatusId: Int = 1,

    @SerializedName("approvalStatus")
    val approvalStatus: String = ApprovalStatus.PENDING,

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
) {
    object ApprovalStatus {
        const val PENDING = "PENDING"
        const val APPROVED = "APPROVED"
        const val REJECTED = "REJECTED"
    }
}
