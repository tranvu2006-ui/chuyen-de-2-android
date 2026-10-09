package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Tài khoản người dùng chung — lưu tại: users/{userId}
 * 4 Roles: ADMIN, CUSTOMER, MERCHANT, DRIVER
 * Mapping: Bảng 1 - users (database_schema.sql)
 */
data class User(
    @SerializedName("uid")
    val uid: String = "",

    @SerializedName("roleId")
    val roleId: Int = 2,

    @SerializedName("role")
    val role: String = Role.CUSTOMER,

    @SerializedName("name")
    val name: String = "",

    @SerializedName("email")
    val email: String = "",

    @SerializedName("phone")
    val phone: String = "",

    @SerializedName("statusId")
    val statusId: Int = 1,

    @SerializedName("status")
    val status: String = Status.ACTIVE,

    @SerializedName("isActive")
    val isActive: Boolean = true,

    @SerializedName("avatarUrl")
    val avatarUrl: String = "",

    @SerializedName("fcmToken")
    val fcmToken: String = ""
) {
    /** Role constants — RBAC phân quyền 4 Actors */
    object Role {
        const val ADMIN = "ADMIN"
        const val CUSTOMER = "CUSTOMER"
        const val MERCHANT = "MERCHANT"
        const val MANAGER = "MANAGER"
        const val DRIVER = "DRIVER"
        const val SHIPPER = "SHIPPER"
    }

    /** Trạng thái tài khoản */
    object Status {
        const val ACTIVE = "ACTIVE"
        const val INACTIVE = "INACTIVE"
        const val OFFLINE = "OFFLINE"
    }
}
