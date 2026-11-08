package com.chuyen_de_2.foodorder.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Tiện ích xử lý và nén ảnh chọn từ thư viện điện thoại (Gallery Picker)
 * - Tự động xoay ảnh theo đúng EXIF Orientation (chống lật/ngang trên camera Samsung/Xiaomi).
 * - Nạp kích thước trước bằng inJustDecodeBounds và dùng inSampleSize tránh OutOfMemoryError.
 * - Chuẩn hóa kích thước tối đa và nén JPEG thành chuỗi Data URI Base64 ("data:image/jpeg;base64,...").
 */
object ImagePickerUtils {

    fun compressUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 600,
        quality: Int = 80
    ): String? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Đọc kích thước ảnh gốc mà không nạp toàn bộ pixel vào RAM
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return null

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Tính sampleSize tối ưu
            var sampleSize = 1
            var halfWidth = origWidth
            var halfHeight = origHeight
            while (halfWidth > maxDimension * 2 || halfHeight > maxDimension * 2) {
                sampleSize *= 2
                halfWidth /= 2
                halfHeight /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decodedBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 3. Xử lý xoay theo EXIF tag orientation
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                contentResolver.openInputStream(uri)?.use { exifInput ->
                    val exif = ExifInterface(exifInput)
                    orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (_: Exception) {}

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            }

            // 4. Scale chính xác về maxDimension
            val currentWidth = decodedBitmap.width
            val currentHeight = decodedBitmap.height
            val scaleFactor = if (currentWidth > maxDimension || currentHeight > maxDimension) {
                val maxCurr = maxOf(currentWidth, currentHeight).toFloat()
                maxDimension.toFloat() / maxCurr
            } else {
                1f
            }
            if (scaleFactor < 1f) {
                matrix.postScale(scaleFactor, scaleFactor)
            }

            val finalBitmap = if (!matrix.isIdentity) {
                Bitmap.createBitmap(decodedBitmap, 0, 0, currentWidth, currentHeight, matrix, true)
            } else {
                decodedBitmap
            }

            // 5. Nén JPEG Base64
            val outputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()

            if (finalBitmap != decodedBitmap) {
                finalBitmap.recycle()
            }
            decodedBitmap.recycle()

            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
