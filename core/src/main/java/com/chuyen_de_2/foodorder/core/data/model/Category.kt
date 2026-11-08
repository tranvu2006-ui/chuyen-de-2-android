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
            Category(categoryId = "cat_com", categoryName = "Cơm", iconUrl = "", displayOrder = 1, isActive = true, description = "Cơm trưa, cơm tấm, cơm văn phòng"),
            Category(categoryId = "cat_pho", categoryName = "Bún - Phở", iconUrl = "", displayOrder = 2, isActive = true, description = "Phở bò, phở gà, bún bò huế, bún chả"),
            Category(categoryId = "cat_drink", categoryName = "Trà sữa & Nước", iconUrl = "", displayOrder = 3, isActive = true, description = "Trà sữa, trà trái cây, nước ép"),
            Category(categoryId = "cat_chicken", categoryName = "Gà rán", iconUrl = "", displayOrder = 4, isActive = true, description = "Gà rán giòn, gà sốt cay"),
            Category(categoryId = "cat_bread", categoryName = "Bánh mì", iconUrl = "", displayOrder = 5, isActive = true, description = "Bánh mì thịt, bánh mì chảo, bánh mì kẹp"),
            Category(categoryId = "cat_pizza", categoryName = "Pizza", iconUrl = "", displayOrder = 6, isActive = true, description = "Pizza phô mai, pizza hải sản"),
            Category(categoryId = "cat_coffee", categoryName = "Cà phê", iconUrl = "", displayOrder = 7, isActive = true, description = "Cà phê sữa, bạc xỉu, americano"),
            Category(categoryId = "cat_snack", categoryName = "Đồ ăn vặt", iconUrl = "", displayOrder = 8, isActive = true, description = "Bánh tráng trộn, khoai tây chiên, nem chua"),
            Category(categoryId = "cat_veg", categoryName = "Món chay", iconUrl = "", displayOrder = 9, isActive = true, description = "Cơm chay, hủ tiếu chay, lẩu nấm"),
            Category(categoryId = "cat_seafood", categoryName = "Hải sản", iconUrl = "", displayOrder = 10, isActive = true, description = "Tôm, cua, mực, ốc, nghêu"),
            Category(categoryId = "cat_dessert", categoryName = "Tráng miệng", iconUrl = "", displayOrder = 11, isActive = true, description = "Chè, kem tươi, bánh ngọt"),
            Category(categoryId = "cat_hotpot", categoryName = "Lẩu / Món Nước", iconUrl = "", displayOrder = 12, isActive = true, description = "Lẩu thái, lẩu riêu, canh kim chi")
        )
    }
}
