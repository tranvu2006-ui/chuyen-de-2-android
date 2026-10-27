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

    @SerializedName("avatarUrl")
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
            trustScore >= 95 -> "💎 Hạng Kim Cương"
            trustScore >= 80 -> "🥇 Hạng Vàng"
            trustScore >= 50 -> "🥈 Hạng Bạc"
            trustScore >= 20 -> "⚠️ Hạn Chế COD"
            else -> "🚫 Đình Chỉ"
        }

    @get:Exclude
    val maxCodLimitVnd: Long
        get() = when {
            trustScore >= 95 -> Long.MAX_VALUE
            trustScore >= 80 -> 500_000L
            trustScore >= 50 -> 150_000L
            else -> 0L
        }

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
            trustScore >= 95 -> "Tỷ lệ nhận đơn $completionRatePercent%. Đặc quyền Hạng Kim Cương: Thanh toán tiền mặt COD không giới hạn & tự động giảm 5.000đ phí giao hàng mọi đơn!"
            trustScore >= 80 -> "Tỷ lệ nhận đơn $completionRatePercent%. Quyền lợi Hạng Vàng: Được thanh toán tiền mặt COD cho đơn hàng lên đến 500.000đ."
            trustScore >= 50 -> "Cảnh báo Hạng Bạc: Chỉ hỗ trợ thanh toán tiền mặt COD tối đa 150.000đ/đơn. Hãy nhận hàng đúng hẹn (+2đ/đơn) để nâng hạng!"
            trustScore >= 20 -> "Hạn chế tín nhiệm (<50 điểm): Đã khóa thanh toán tiền mặt COD. Vui lòng thanh toán Chuyển khoản VietQR để tích lũy lại điểm (+2đ/đơn)."
            else -> "Tài khoản bị khóa tự động do điểm uy tín dưới 20 điểm vì bom hàng nhiều lần."
        }
}
