package com.chuyen_de_2.foodorder.core.data.model

import com.google.gson.annotations.SerializedName

/**
 * Wrapper cho response từ REST API
 */
data class ApiResponse<T>(
    @SerializedName("data")
    val data: T? = null,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("success")
    val success: Boolean = true
)
