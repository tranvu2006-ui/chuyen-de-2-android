package com.chuyen_de_2.foodorder.core.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.gson.annotations.SerializedName

/**
 * Danh mục ngành hàng thực phẩm
 * Giao diện: khách/b1.html — Grid danh mục (Cơm, Trà sữa, Phở, Gà...)
 * Mapping: Bảng 4 - categories (database_schema.sql)
 */
@IgnoreExtraProperties
data class Category(
    @SerializedName("categoryId", alternate = ["id"])
    val categoryId: String = "",

    @SerializedName("categoryName", alternate = ["name"])
    val categoryName: String = "",

    @SerializedName("iconUrl", alternate = ["icon"])
    val iconUrl: String = "",

    @SerializedName("displayOrder")
    val displayOrder: Int = 0,

    @SerializedName("isActive")
    val isActive: Boolean = true
) {
    @get:Exclude
    val id: String get() = categoryId

    @get:Exclude
    val name: String get() = categoryName

    @get:Exclude
    val icon: String get() = iconUrl

    companion object {
        fun getDefaultCategories(): List<Category> = listOf(
            Category(categoryId = "cat_com", categoryName = "Cơm", iconUrl = "🍚", displayOrder = 1, isActive = true),
            Category(categoryId = "cat_pho", categoryName = "Bún - Phở", iconUrl = "🍜", displayOrder = 2, isActive = true),
            Category(categoryId = "cat_drink", categoryName = "Trà sữa & Nước", iconUrl = "🧋", displayOrder = 3, isActive = true),
            Category(categoryId = "cat_chicken", categoryName = "Gà rán", iconUrl = "🍗", displayOrder = 4, isActive = true),
            Category(categoryId = "cat_bread", categoryName = "Bánh mì", iconUrl = "🥖", displayOrder = 5, isActive = true),
            Category(categoryId = "cat_pizza", categoryName = "Pizza", iconUrl = "🍕", displayOrder = 6, isActive = true),
            Category(categoryId = "cat_coffee", categoryName = "Cà phê", iconUrl = "☕", displayOrder = 7, isActive = true),
            Category(categoryId = "cat_snack", categoryName = "Đồ ăn vặt", iconUrl = "🍟", displayOrder = 8, isActive = true),
            Category(categoryId = "cat_veg", categoryName = "Món chay", iconUrl = "🥗", displayOrder = 9, isActive = true),
            Category(categoryId = "cat_seafood", categoryName = "Hải sản", iconUrl = "🦐", displayOrder = 10, isActive = true),
            Category(categoryId = "cat_dessert", categoryName = "Tráng miệng", iconUrl = "🍰", displayOrder = 11, isActive = true),
            Category(categoryId = "cat_hotpot", categoryName = "Lẩu / Món Nước", iconUrl = "🍲", displayOrder = 12, isActive = true)
        )
    }
}
