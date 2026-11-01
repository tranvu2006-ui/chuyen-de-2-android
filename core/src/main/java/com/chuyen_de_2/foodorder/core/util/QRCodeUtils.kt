package com.chuyen_de_2.foodorder.core.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Tiện ích sinh mã QR chuẩn quốc tế ISO/IEC 18004 (ZXing) và xác thực mã QR đối soát giao hàng
 * giữa ứng dụng Khách hàng (app-customer) và ứng dụng Tài xế Shipper (app-internal).
 */
object QRCodeUtils {

    data class QrVerifyResult(
        val isValid: Boolean,
        val matchedToken: String,
        val message: String
    )

    /**
     * Lấy mã rút gọn 6 ký tự in hoa của đơn hàng (VD: "88291A")
     */
    fun getShortOrderCode(orderId: String): String {
        val clean = orderId.trim()
        return if (clean.length > 6) clean.takeLast(6).uppercase() else clean.uppercase()
    }

    /**
     * Chuỗi Delivery Token hiển thị trên cả màn hình Khách hàng và màn hình Shipper
     */
    fun getDisplayToken(orderId: String, deliveryToken: String = ""): String {
        val shortCode = getShortOrderCode(orderId)
        val cleanToken = deliveryToken.trim()
        return when {
            cleanToken.startsWith("TOKEN_OISHI_", ignoreCase = true) -> cleanToken.uppercase()
            shortCode.isNotBlank() -> "TOKEN_OISHI_$shortCode"
            cleanToken.isNotBlank() -> cleanToken.uppercase()
            else -> "TOKEN_OISHI_ORDER"
        }
    }

    /**
     * Tạo chuỗi dữ liệu chuẩn hóa nhúng vào mã QR trên ứng dụng Khách hàng.
     * Bao gồm cả orderId đầy đủ, mã rút gọn 6 ký tự và deliveryToken để Shipper quét chính xác 100%.
     */
    fun buildOrderQrPayload(orderId: String, deliveryToken: String = ""): String {
        val cleanOrderId = orderId.trim()
        val shortCode = getShortOrderCode(cleanOrderId)
        val displayToken = getDisplayToken(cleanOrderId, deliveryToken)
        val pin = deliveryToken.trim().ifBlank { displayToken }
        return "OISHI_DELIVERY|ORDER:$cleanOrderId|CODE:$shortCode|TOKEN:$displayToken|PIN:$pin"
    }

    /**
     * Xác minh dữ liệu QR mà Camera của Shipper vừa quét được từ điện thoại của Khách hàng.
     */
    fun verifyOrderQrPayload(
        scannedRaw: String,
        expectedOrderId: String,
        expectedDeliveryToken: String = ""
    ): QrVerifyResult {
        val raw = scannedRaw.trim()
        if (raw.isBlank()) {
            return QrVerifyResult(
                isValid = false,
                matchedToken = "",
                message = "Không đọc được nội dung mã QR. Vui lòng đưa camera lại gần mã QR của khách."
            )
        }

        val expectedOrderClean = expectedOrderId.trim()
        val expectedShortCode = getShortOrderCode(expectedOrderClean)
        val expectedDisplayToken = getDisplayToken(expectedOrderClean, expectedDeliveryToken)
        val expectedPin = expectedDeliveryToken.trim()

        // 1. Trường hợp quét đúng định dạng chuẩn OISHI_DELIVERY|ORDER:...|CODE:...|TOKEN:...|PIN:...
        if (raw.startsWith("OISHI_DELIVERY|", ignoreCase = true)) {
            val parts = raw.split("|")
            var scannedOrderId = ""
            var scannedShortCode = ""
            var scannedToken = ""
            var scannedPin = ""

            for (part in parts) {
                val kv = part.split(":", limit = 2)
                if (kv.size == 2) {
                    val key = kv[0].trim().uppercase()
                    val value = kv[1].trim()
                    when (key) {
                        "ORDER" -> scannedOrderId = value
                        "CODE" -> scannedShortCode = value.uppercase()
                        "TOKEN" -> scannedToken = value.uppercase()
                        "PIN" -> scannedPin = value
                    }
                }
            }

            val isOrderMatch = expectedOrderClean.isNotBlank() &&
                scannedOrderId.equals(expectedOrderClean, ignoreCase = true)
            val isShortCodeMatch = expectedShortCode.isNotBlank() &&
                scannedShortCode.equals(expectedShortCode, ignoreCase = true)
            val isTokenMatch = scannedToken.equals(expectedDisplayToken, ignoreCase = true)
            val isPinMatch = expectedPin.isNotBlank() &&
                scannedPin.equals(expectedPin, ignoreCase = true)

            return if (isOrderMatch || isShortCodeMatch || isTokenMatch || isPinMatch) {
                val tokenToShow = scannedToken.ifBlank { expectedDisplayToken }
                QrVerifyResult(
                    isValid = true,
                    matchedToken = tokenToShow,
                    message = "✓ Mã QR hợp lệ! Khớp đơn hàng #ORD-$expectedShortCode ($tokenToShow)"
                )
            } else {
                val otherCode = scannedShortCode.ifBlank { scannedOrderId.takeLast(6).uppercase() }
                QrVerifyResult(
                    isValid = false,
                    matchedToken = scannedToken.ifBlank { raw },
                    message = "❌ Mã QR này thuộc đơn hàng khác (#ORD-$otherCode), không khớp với đơn đang giao (#ORD-$expectedShortCode)!"
                )
            }
        }

        // 2. Trường hợp quét mã QR chỉ chứa trực tiếp chuỗi TOKEN_OISHI_XXXXXX, orderId, shortCode hoặc mã PIN 6 số
        val normalized = raw.uppercase()
        val isDirectMatch =
            normalized == expectedDisplayToken.uppercase() ||
                (expectedOrderClean.isNotBlank() && normalized == expectedOrderClean.uppercase()) ||
                (expectedShortCode.isNotBlank() && (
                    normalized == expectedShortCode ||
                        normalized == "#ORD_$expectedShortCode" ||
                        normalized == "#ORD-$expectedShortCode" ||
                        normalized == "ORD_$expectedShortCode" ||
                        normalized == "ORD-$expectedShortCode" ||
                        normalized.endsWith("TOKEN_OISHI_$expectedShortCode")
                    )) ||
                (expectedPin.isNotBlank() && normalized == expectedPin.uppercase())

        return if (isDirectMatch) {
            QrVerifyResult(
                isValid = true,
                matchedToken = expectedDisplayToken,
                message = "✓ Đã xác minh mã nhận hàng hợp lệ! Khớp đơn #ORD-$expectedShortCode"
            )
        } else {
            QrVerifyResult(
                isValid = false,
                matchedToken = raw.take(32),
                message = "❌ Mã QR không khớp với đơn hàng #ORD-$expectedShortCode! Vui lòng quét đúng mã trên app Khách hàng."
            )
        }
    }

    /**
     * Tạo chuỗi dữ liệu QR chuyển tiền nội bộ Ví MoMo chuẩn (2|99|phone|name||0|0|amount|note|transfer_myqr).
     * Khi quét bằng App MoMo thật sẽ mở đúng màn hình "Chuyển tiền đến Ví MoMo" (không bị nhảy sang "Đến ngân hàng" báo lỗi tài khoản không hợp lệ).
     */
    fun buildMomoQrPayload(
        phone: String,
        receiverName: String,
        amount: Long,
        transferSyntax: String
    ): String {
        val cleanPhone = phone.replace(Regex("[^0-9]"), "").ifBlank { "0988888888" }
        val cleanName = removeAccents(receiverName).replace(Regex("[^A-Za-z0-9 ]"), " ").trim().uppercase().ifBlank { "OISHI FOOD" }
        val cleanNote = removeAccents(transferSyntax).replace(Regex("[^A-Za-z0-9 ]"), " ").trim().uppercase().ifBlank { "OISHI" }
        val safeAmount = amount.coerceAtLeast(0L)
        return "2|99|$cleanPhone|$cleanName||0|0|$safeAmount|$cleanNote|transfer_myqr"
    }

    private fun removeAccents(input: String): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return normalized
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
    }

    /**
     * Tạo Bitmap QR Code thực tế theo chuẩn quốc tế ISO/IEC 18004 bằng ZXing QRCodeWriter.
     * Đảm bảo mọi camera điện thoại / máy quét QR đều nhận diện được ngay lập tức.
     */
    fun generateQRCodeBitmap(
        text: String,
        width: Int = 512,
        height: Int = 512,
        darkColorHex: String = "#0F172A"
    ): Bitmap {
        val content = text.ifBlank { "OISHI_DELIVERY_EMPTY" }
        val safeWidth = width.coerceAtLeast(200)
        val safeHeight = height.coerceAtLeast(200)

        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                safeWidth,
                safeHeight,
                hints
            )
            val pixels = IntArray(safeWidth * safeHeight)
            val darkColor = try {
                Color.parseColor(darkColorHex)
            } catch (_: Exception) {
                Color.parseColor("#0F172A")
            }
            for (y in 0 until safeHeight) {
                val offset = y * safeWidth
                for (x in 0 until safeWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else Color.WHITE
                }
            }
            Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, safeWidth, 0, 0, safeWidth, safeHeight)
            }
        } catch (e: Exception) {
            Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
        }
    }
}
