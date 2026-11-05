package com.chuyen_de_2.foodorder.core.data.model

import com.google.firebase.database.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName

/**
 * Khiếu nại đơn hàng & Báo cáo vi phạm — lưu tại: complaints/{complaintId}
 * Giao diện: khách/b12.html — Gửi khiếu nại
 *            admin.html — Xử lý khiếu nại & phán quyết 3 mức chế tài vi phạm
 * Mapping: Bảng 14 - complaints (database_schema.sql)
 */
@IgnoreExtraProperties
data class Complaint(
    @SerializedName("complaintId")
    val complaintId: String = "",

    @SerializedName("orderId")
    val orderId: String = "",

    // Bên khiếu nại (Nguồn gửi)
    @SerializedName("senderType")
    val senderType: String = SenderType.CUSTOMER, // CUSTOMER, STORE, DRIVER

    @SerializedName("customerId")
    val customerId: String = "",

    @SerializedName("senderName")
    val senderName: String = "",

    @SerializedName("senderPhone")
    val senderPhone: String = "",

    // Đối tượng bị khiếu nại (Bên vi phạm)
    @SerializedName("targetType")
    val targetType: String = TargetType.STORE, // STORE, CUSTOMER, DRIVER

    @SerializedName("targetId")
    val targetId: String = "",

    @SerializedName("targetName")
    val targetName: String = "",

    @SerializedName("targetPhone")
    val targetPhone: String = "",

    @SerializedName("storeId")
    val storeId: String = "",

    @SerializedName("driverId")
    val driverId: String = "",

    // Phân loại vấn đề & Nội dung
    @SerializedName("complaintType")
    val complaintType: String = ComplaintType.OTHER,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("reason")
    val reason: String = "",

    @SerializedName("evidenceImage")
    val evidenceImage: String = "",

    @SerializedName("priority")
    val priority: String = Priority.MEDIUM,

    // Trạng thái & Phán quyết của Ban Quản Trị
    @SerializedName("status")
    val status: String = ComplaintStatus.PENDING,

    @SerializedName("penaltyApplied")
    val penaltyApplied: String = "NONE", // WARN, HIDE, LOCK, NONE

    @SerializedName("adminNote")
    val adminNote: String = "",

    // Chế tài phạt ngược người khiếu nại (báo cáo sai / vu khống / lạm dụng hệ thống)
    @SerializedName("isComplainantPenalized")
    val isComplainantPenalized: Boolean = false,

    @SerializedName("counterPenaltyApplied")
    val counterPenaltyApplied: String = "NONE", // WARN, HIDE, LOCK, NONE

    @SerializedName("counterPenaltyReason")
    val counterPenaltyReason: String = "",

    @SerializedName("trustScoreDeducted")
    val trustScoreDeducted: Int = 0,

    @SerializedName("createdAt")
    val createdAt: Long = 0,

    @SerializedName("resolvedAt")
    val resolvedAt: Long = 0
) {
    object ComplaintStatus {
        const val PENDING = "PENDING"
        const val RESOLVED = "RESOLVED"
        const val REJECTED = "REJECTED"
    }

    object SenderType {
        const val CUSTOMER = "CUSTOMER"
        const val STORE = "STORE"
        const val DRIVER = "DRIVER"
    }

    object TargetType {
        const val STORE = "STORE"
        const val CUSTOMER = "CUSTOMER"
        const val DRIVER = "DRIVER"
    }

    object ComplaintType {
        const val ATTP = "ATTP"                     // An toàn vệ sinh thực phẩm
        const val WRONG_ITEM = "WRONG_ITEM"         // Giao sai món / thiếu món
        const val ATTITUDE = "ATTITUDE"             // Thái độ phục vụ
        const val DELIVERY_DELAY = "DELIVERY_DELAY" // Giao quá trễ
        const val BOM_ORDER = "BOM_ORDER"           // Khách bom hàng / không nhận
        const val FRAUD = "FRAUD"                   // Gian lận / sai tiền
        const val OTHER = "OTHER"                   // Khác
    }

    object Priority {
        const val HIGH = "HIGH"
        const val MEDIUM = "MEDIUM"
        const val LOW = "LOW"
    }
}
