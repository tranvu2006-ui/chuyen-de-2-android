package com.chuyen_de_2.foodorder.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tiện ích xử lý và nén ảnh chọn từ thư viện điện thoại (Gallery Picker)
 * - Tự động đọc toàn bộ byte 1 lần duy nhất từ ContentProvider (chống lỗi SecurityException / StreamClosed khi mở nhiều lần trên Android 10+).
 * - Tự động xoay ảnh theo đúng EXIF Orientation (chống lật/ngang trên camera Samsung/Xiaomi).
 * - Sử dụng inSampleSize và scaledBitmap để tối ưu RAM và dung lượng lưu trữ trên Firebase.
 * - Chuẩn hóa kích thước tối đa và nén JPEG thành chuỗi Data URI Base64 ("data:image/jpeg;base64,...").
 */
object ImagePickerUtils {

    fun compressUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 300,
        quality: Int = 80
    ): String? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Đọc toàn bộ byte một lần duy nhất từ ContentProvider
            val rawBytes = contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return null

            if (rawBytes.isEmpty()) return null

            // 2. Đọc kích thước ảnh gốc mà không nạp toàn bộ pixel vào RAM
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                // Fallback decode trực tiếp nếu boundsOptions không lấy được size
                val directBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
                return compressBitmapToBase64(directBitmap, maxDimension, quality)
            }

            // 3. Tính sampleSize tối ưu
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

            val decodedBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: return null

            // 4. Xử lý xoay theo EXIF tag orientation từ ByteArrayInputStream
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                ByteArrayInputStream(rawBytes).use { exifInput ->
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

            // 5. Scale chính xác về maxDimension
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

            // 6. Nén JPEG Base64
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

    fun compressBitmapToBase64(
        sourceBitmap: Bitmap,
        maxDimension: Int = 300,
        quality: Int = 80
    ): String? {
        return try {
            val width = sourceBitmap.width
            val height = sourceBitmap.height
            val maxCurr = maxOf(width, height)
            val scaledBitmap = if (maxCurr > maxDimension) {
                val scale = maxDimension.toFloat() / maxCurr.toFloat()
                val targetW = maxOf(1, (width * scale).toInt())
                val targetH = maxOf(1, (height * scale).toInt())
                Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
            } else {
                sourceBitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()

            if (scaledBitmap != sourceBitmap) {
                scaledBitmap.recycle()
            }

            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
