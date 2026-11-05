package com.chuyen_de_2.foodorder.core.util

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.util.Base64
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chuyen_de_2.foodorder.core.R

/**
 * Tiện ích tải ảnh đồng bộ hỗ trợ:
 * - Chuỗi Data URI Base64 ("data:image/...;base64,...")
 * - Chuỗi Base64 thuần (>300 ký tự)
 * - Đường dẫn URL HTTP / HTTPS
 * - Tự động bo góc (cornerDp) và fallback placeholder khi trống hoặc lỗi
 * - Tự động gỡ bỏ/khôi phục Tint cho avatar để chống lỗi ảnh bị ám màu
 */
object ImageLoadUtils {

    fun loadStoreImage(
        imageView: ImageView,
        imageUrl: String?,
        placeholderResId: Int = R.drawable.ic_store,
        errorResId: Int = placeholderResId,
        cornerDp: Int = 0
    ) {
        val raw = imageUrl?.trim().orEmpty()
        val context = imageView.context ?: return

        if (raw.isBlank()) {
            try {
                Glide.with(context).clear(imageView)
            } catch (_: Exception) {}
            imageView.setImageResource(placeholderResId)
            return
        }

        try {
            val requestManager = Glide.with(context)
            val isBase64 = raw.startsWith("data:image", ignoreCase = true) || raw.length > 300

            if (isBase64) {
                val clean = if (raw.contains(",")) raw.substringAfter(",") else raw
                val decodedBytes = Base64.decode(clean, Base64.DEFAULT)
                var req = requestManager.asBitmap().load(decodedBytes)
                if (cornerDp > 0) {
                    val px = (cornerDp * context.resources.displayMetrics.density).toInt()
                    req = req.transform(CenterCrop(), RoundedCorners(px))
                }
                req.placeholder(placeholderResId)
                    .error(errorResId)
                    .into(imageView)
            } else {
                var req = requestManager.load(raw)
                if (cornerDp > 0) {
                    val px = (cornerDp * context.resources.displayMetrics.density).toInt()
                    req = req.transform(CenterCrop(), RoundedCorners(px))
                }
                req.placeholder(placeholderResId)
                    .error(errorResId)
                    .into(imageView)
            }
        } catch (_: Exception) {
            imageView.setImageResource(errorResId)
        }
    }

    /**
     * Tải avatar người dùng hỗ trợ cả Base64, Data URI và URL trực tuyến:
     * - Tự động xóa tint khi tải ảnh thật để tránh biến dạng màu ảnh chân dung.
     * - Cắt tròn tự động (circleCrop).
     * - Khôi phục màu sắc biểu tượng mặc định khi người dùng chưa có ảnh.
     */
    fun loadUserAvatar(
        imageView: ImageView,
        avatarUrl: String?,
        placeholderResId: Int = R.drawable.ic_user,
        errorResId: Int = placeholderResId,
        defaultTintColor: Int? = null
    ) {
        val raw = avatarUrl?.trim().orEmpty()
        val context = imageView.context ?: return

        if (raw.isBlank()) {
            try {
                Glide.with(context).clear(imageView)
            } catch (_: Exception) {}
            imageView.setImageResource(placeholderResId)
            if (defaultTintColor != null) {
                imageView.imageTintList = ColorStateList.valueOf(defaultTintColor)
                imageView.setColorFilter(defaultTintColor)
            } else {
                imageView.imageTintList = null
                imageView.clearColorFilter()
            }
            return
        }

        // Đảm bảo gỡ bỏ tint màu trước khi nạp ảnh chân dung thật
        imageView.imageTintList = null
        imageView.clearColorFilter()

        try {
            val requestManager = Glide.with(context)
            val isBase64 = raw.startsWith("data:image", ignoreCase = true) || raw.length > 300

            if (isBase64) {
                val clean = if (raw.contains(",")) raw.substringAfter(",") else raw
                val decodedBytes = Base64.decode(clean, Base64.DEFAULT)
                requestManager.asBitmap()
                    .load(decodedBytes)
                    .circleCrop()
                    .placeholder(placeholderResId)
                    .error(errorResId)
                    .into(imageView)
            } else {
                requestManager
                    .load(raw)
                    .circleCrop()
                    .placeholder(placeholderResId)
                    .error(errorResId)
                    .into(imageView)
            }
        } catch (_: Exception) {
            imageView.setImageResource(errorResId)
            if (defaultTintColor != null) {
                imageView.imageTintList = ColorStateList.valueOf(defaultTintColor)
                imageView.setColorFilter(defaultTintColor)
            }
        }
    }

    /**
     * Helper tiện ích nạp avatar kèm ID tài nguyên màu mặc định (R.color.xxx)
     */
    fun loadDishImage(imageView: ImageView, imageUrl: String?, placeholderResId: Int = R.drawable.ic_store, errorResId: Int = placeholderResId, cornerDp: Int = 0) = loadStoreImage(imageView, imageUrl, placeholderResId, errorResId, cornerDp)

    fun loadUserAvatarWithTintRes(
        imageView: ImageView,
        avatarUrl: String?,
        placeholderResId: Int = R.drawable.ic_user,
        errorResId: Int = placeholderResId,
        tintColorResId: Int? = null
    ) {
        val color = tintColorResId?.let { ContextCompat.getColor(imageView.context, it) }
        loadUserAvatar(imageView, avatarUrl, placeholderResId, errorResId, color)
    }
}
