package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Bảng 4: admins — Hồ sơ Quản trị viên hệ thống (Actor ADMIN)
 * Lưu tại: admins/{adminId}
 * Chịu trách nhiệm vận hành sàn, kiểm duyệt đối tác và giải quyết khiếu nại
 */
data class Admin(
    @SerializedName("userId")
    val userId: String = "",

    @SerializedName("adminId")
    val adminId: String = userId,

    @SerializedName("adminCode")
    val adminCode: String = "",

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
)

