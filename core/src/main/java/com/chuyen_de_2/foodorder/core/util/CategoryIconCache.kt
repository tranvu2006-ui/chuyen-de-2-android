package com.chuyen_de_2.foodorder.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.collection.LruCache

/**
 * Cache bộ nhớ đệm (In-Memory LRU Cache) cho ảnh Icon Danh mục (Base64 và Bitmap)
 * Giúp hiển thị tức thì (0ms) trên RecyclerView, loại bỏ hoàn toàn độ trễ giải mã
 * và hiện tượng giật/chớp icon mặc định khi chuyển đổi nhanh giữa các danh mục.
 */
object CategoryIconCache {

    // Lưu tối đa 64 icon danh mục trong RAM
    private val bitmapCache = LruCache<String, Bitmap>(64)

    fun get(key: String): Bitmap? {
        if (key.isBlank()) return null
        return bitmapCache.get(key)
    }

    fun put(key: String, bitmap: Bitmap) {
        if (key.isNotBlank()) {
            bitmapCache.put(key, bitmap)
        }
    }

    /**
     * Lấy Bitmap từ cache nếu có sẵn; nếu chưa có thì giải mã Base64 và lưu cache ngay.
     * Trả về Bitmap đồng bộ tức thì, tránh qua thread background của Glide.
     */
    fun getOrDecode(iconStr: String): Bitmap? {
        val trimmed = iconStr.trim()
        if (trimmed.isBlank()) return null

        val cached = bitmapCache.get(trimmed)
        if (cached != null) return cached

        // Nếu là Base64 hoặc chuỗi byte ảnh lớn
        if (trimmed.startsWith("data:image", ignoreCase = true) || trimmed.length > 100) {
            return try {
                val clean = if (trimmed.contains(",")) trimmed.substringAfter(",") else trimmed
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    bitmapCache.put(trimmed, bmp)
                }
                bmp
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    fun clear() {
        bitmapCache.evictAll()
    }
}
