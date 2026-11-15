package com.chuyen_de_2.foodorder.core.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Chef(
    val chefId: String = "",
    val storeId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val title: String = "Đầu Bếp",
    val activeOrdersCount: Int = 0,
    val isOnline: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
