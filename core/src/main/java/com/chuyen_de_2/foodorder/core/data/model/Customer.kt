package com.chuyen_de_2.foodorder.core.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName

/**
 * Hồ sơ khách hàng — lưu tại: customers/{customerId}
 * Giao diện: khách/b11.html — Trang cá nhân + Thẻ Điểm Uy Tín
 * Mapping: Bảng 2 - customers (database_schema.sql)
 */
@IgnoreExtraProperties
data class Customer(
    @SerializedName("customerId")
    val customerId: String = "",

    @SerializedName("avatarUrl", alternate = ["photoUrl", "avatar", "profileImage"])
    val avatarUrl: String = "",

    @SerializedName("trustScore")
    val trustScore: Int = 100,

    @SerializedName("totalOrders")
    val totalOrders: Int = 0,

    @SerializedName("completedOrders")
    val completedOrders: Int = 0,

    @SerializedName("boomOrders")
    val boomOrders: Int = 0
) {
    @get:Exclude
    val tierBadge: String
        get() = when {
            trustScore >= 95 -> "💎 Hạng Kim Cương (COD)"
            trustScore >= 90 -> "🥇 Hạng Vàng (COD)"
            trustScore >= 50 -> "🥈 Hạng Bạc (Cấm COD)"
            trustScore >= 20 -> "⚠️ Hạn Chế COD"
            else -> "🚫 Đình Chỉ"
        }

    @get:Exclude
    val maxCodLimitVnd: Long
        get() = if (trustScore >= 90) Long.MAX_VALUE else 0L

    @get:Exclude
    val isCodEligible: Boolean
        get() = trustScore >= 90

    @get:Exclude
    val shippingDiscountVnd: Long
        get() = if (trustScore >= 95) 5_000L else 0L

    @get:Exclude
    val completionRatePercent: Int
        get() = if (totalOrders > 0) {
            ((completedOrders.toFloat() / totalOrders.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            100
        }

    @get:Exclude
    val privilegeDescription: String
        get() = when {
            trustScore >= 90 -> "Tỷ lệ nhận đơn $completionRatePercent%. Đủ điều kiện thanh toán tiền mặt COD (từ 90 điểm trở lên)${if (trustScore >= 95) " & giảm 5.000đ ship" else ""}."
            else -> "Điểm uy tín dưới 90 điểm ($trustScore/100): Đã khóa hình thức COD. Vui lòng chọn Đặt cọc 20% hoặc Chuyển khoản VietQR để tích lũy lại điểm (+2đ/đơn)."
        }
}
