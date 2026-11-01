package com.chuyen_de_2.foodorder.customer.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Model Quán ăn yêu thích
 */
data class FavoriteStore(
    val storeId: String,
    val storeName: String,
    val storeAddress: String = "",
    val storePhone: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Quản lý danh sách Quán ăn yêu thích lưu trong SharedPreferences của máy
 * Giúp khách hàng lưu lại những quán quen để mở lại đặt món nhanh chóng từ Trang cá nhân
 */
object FavoriteStoreManager {

    private const val PREFS_NAME = "oishi_favorite_stores"
    private const val KEY_FAVORITES = "key_favorite_list"
    private val gson = Gson()

    fun getFavorites(context: Context): List<FavoriteStore> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FavoriteStore>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFavorite(context: Context, storeId: String): Boolean {
        if (storeId.isBlank()) return false
        val list = getFavorites(context)
        return list.any { it.storeId == storeId }
    }

    fun addFavorite(context: Context, store: FavoriteStore) {
        val list = getFavorites(context).toMutableList()
        if (list.none { it.storeId == store.storeId }) {
            list.add(0, store) // Đưa lên đầu danh sách
            saveList(context, list)
        }
    }

    fun removeFavorite(context: Context, storeId: String) {
        val list = getFavorites(context).toMutableList()
        val removed = list.removeAll { it.storeId == storeId }
        if (removed) {
            saveList(context, list)
        }
    }

    fun toggleFavorite(context: Context, store: FavoriteStore): Boolean {
        return if (isFavorite(context, store.storeId)) {
            removeFavorite(context, store.storeId)
            false
        } else {
            addFavorite(context, store)
            true
        }
    }

    private fun saveList(context: Context, list: List<FavoriteStore>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(list)).apply()
    }
}
