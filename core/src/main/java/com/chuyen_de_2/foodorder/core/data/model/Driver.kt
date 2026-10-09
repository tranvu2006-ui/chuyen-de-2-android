package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Tài xế nội bộ quán — lưu tại: drivers/{driverId}
 * Giao diện: Quán/1.html — Quản lý tài xế nội bộ
 *            ship/2.html — Vào ca trực tuyến
 * Mapping: Bảng 10 - drivers (database_schema.sql)
 *
 * Tài xế gắn liền với từng quán ăn (TX-19, TX-14, TX-08),
 * không phải shipper tự do.
 */
data class Driver(
    @SerializedName("driverId")
    val driverId: String = "",

    @SerializedName("storeId")
    val storeId: String = "",

    @SerializedName("driverCode")
    val driverCode: String = "",

    @SerializedName("cccdNumber")
    val cccdNumber: String = "",

    @SerializedName("cccdFrontUrl")
    val cccdFrontUrl: String = "",

    @SerializedName("cccdBackUrl")
    val cccdBackUrl: String = "",

    @SerializedName("licenseNumber")
    val licenseNumber: String = "",

    @SerializedName("vehicleType")
    val vehicleType: String = VehicleType.MOTORBIKE,

    @SerializedName("vehiclePlate")
    val vehiclePlate: String = "",

    @SerializedName("ratingAvg")
    val ratingAvg: Float = 5.0f,

    @SerializedName("dutyStatus")
    val dutyStatus: String = DutyStatus.OFF_DUTY,

    @SerializedName("currentLat")
    val currentLat: Double = 0.0,

    @SerializedName("currentLng")
    val currentLng: Double = 0.0,

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Loại phương tiện */
    object VehicleType {
        const val MOTORBIKE = "MOTORBIKE"
        const val ELECTRIC_BIKE = "ELECTRIC_BIKE"
    }

    /** Trạng thái ca trực */
    object DutyStatus {
        const val AVAILABLE = "AVAILABLE"
        const val DELIVERING = "DELIVERING"
        const val OFF_DUTY = "OFF_DUTY"
    }
}
