package com.chuyen_de_2.foodorder.core.data.model

import java.io.Serializable

data class AddressSuggestion(
    val title: String,
    val subtitle: String,
    val fullAddress: String,
    val district: String,
    val latitude: Double,
    val longitude: Double
) : Serializable
