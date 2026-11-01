package com.chuyen_de_2.foodorder.core.util

import android.graphics.Bitmap
import android.util.Base64
import android.widget.ImageView
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
}
