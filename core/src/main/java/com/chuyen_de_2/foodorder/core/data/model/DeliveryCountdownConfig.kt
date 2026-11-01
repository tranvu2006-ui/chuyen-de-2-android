package com.chuyen_de_2.foodorder.core.data.model

import androidx.annotation.Keep

@Keep
data class DeliveryCountdownConfig(
    val round1Minutes: Int = 15,
    val round2Minutes: Int = 15,
    val updatedAt: Long = 0L
)
