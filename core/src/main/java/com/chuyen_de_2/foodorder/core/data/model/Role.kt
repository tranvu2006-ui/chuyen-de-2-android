package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Bảng 1: roles — Danh mục vai trò và quyền hạn (RBAC)
 * Quản lý 4 nhóm tác nhân: ADMIN (1), CUSTOMER (2), MERCHANT (3), DRIVER (4)
 */
data class Role(
    @SerializedName("roleId")
    val roleId: Int = 0,

    @SerializedName("roleCode")
    val roleCode: String = "",

    @SerializedName("roleName")
    val roleName: String = "",

    @SerializedName("description")
    val description: String = ""
) {
    companion object {
        val DEFAULT_ROLES = listOf(
            Role(1, "ADMIN", "Quản trị viên", "Toàn quyền quản trị hệ thống, kiểm duyệt quán và xử lý khiếu nại"),
            Role(2, "CUSTOMER", "Khách hàng", "Người dùng mua đồ ăn, tạo giỏ hàng, đặt đơn và đánh giá món ăn"),
            Role(3, "MERCHANT", "Chủ cửa hàng", "Quản lý thực đơn, duyệt đơn hàng và điều phối tài xế nội bộ"),
            Role(4, "DRIVER", "Tài xế giao hàng", "Tài xế nội bộ của quán, nhận đơn, định vị GPS và xác thực QR")
        )
    }
}
