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
    val isActive: Boolean = true,

    @SerializedName("description", alternate = ["desc"])
    val description: String = ""
) {
    @get:Exclude
    val id: String get() = categoryId

    @get:Exclude
    val name: String get() = categoryName

    @get:Exclude
    val icon: String get() = iconUrl

    @get:Exclude
    val isImageUrl: Boolean
        get() = iconUrl.startsWith("http://", ignoreCase = true) ||
                iconUrl.startsWith("https://", ignoreCase = true) ||
                iconUrl.startsWith("data:image", ignoreCase = true) ||
                iconUrl.length > 100

    companion object {
        fun getDefaultCategories(): List<Category> = listOf(
            Category(categoryId = "cat_com", categoryName = "Cơm", iconUrl = "", displayOrder = 1, isActive = true, description = "Cơm trưa, cơm tấm, cơm văn phòng, cơm gà"),
            Category(categoryId = "cat_pho", categoryName = "Phở", iconUrl = "", displayOrder = 2, isActive = true, description = "Phở bò, phở gà, phở tái nạm, phở đặc biệt"),
            Category(categoryId = "cat_tra_sua", categoryName = "Trà sữa", iconUrl = "", displayOrder = 3, isActive = true, description = "Trà sữa trân châu, trà đào, macchiato"),
            Category(categoryId = "cat_bun_dau", categoryName = "Bún đậu", iconUrl = "", displayOrder = 4, isActive = true, description = "Bún đậu mắm tôm, bún đậu thập cẩm"),
            Category(categoryId = "cat_nuoc_ep", categoryName = "Nước ép", iconUrl = "", displayOrder = 5, isActive = true, description = "Nước ép hoa quả tươi, sinh tố, detox")
        )
    }
}
