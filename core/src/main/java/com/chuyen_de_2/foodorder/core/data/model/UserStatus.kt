package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Bảng 2: user_statuses — Danh mục trạng thái tài khoản người dùng
 * Quản lý trạng thái đăng nhập & hoạt động: ACTIVE (1), INACTIVE (2), OFFLINE (3)
 */
data class UserStatus(
    @SerializedName("statusId")
    val statusId: Int = 1,

    @SerializedName("statusCode")
    val statusCode: String = "",

    @SerializedName("statusName")
    val statusName: String = "",

    @SerializedName("description")
    val description: String = ""
) {
    companion object {
        val DEFAULT_STATUSES = listOf(
            UserStatus(1, "ACTIVE", "Đang hoạt động", "Tài khoản đang trực tuyến và có thể tương tác bình thường"),
            UserStatus(2, "INACTIVE", "Ngưng hoạt động", "Tài khoản tạm ngừng hoạt động hoặc chưa kích hoạt"),
            UserStatus(3, "OFFLINE", "Ngoại tuyến", "Tài khoản đã đăng xuất hoặc ở trạng thái nghỉ ca")
        )
    }
}
