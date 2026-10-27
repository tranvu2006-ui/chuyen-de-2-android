package com.chuyen_de_2.foodorder.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Client gọi trực tiếp API Cổng Thanh Toán MoMo v2 (https://test-payment.momo.vn/v2/gateway/api)
 * - Tạo đơn thanh toán QR MoMo (/create) có chữ ký điện tử HmacSHA256
 * - Tự động truy vấn trạng thái giao dịch (/query) theo thời gian thực để biết khách đã chuyển tiền hay chưa
 */
object MomoPaymentGatewayClient {

    private const val ENDPOINT_CREATE = "https://test-payment.momo.vn/v2/gateway/api/create"
    private const val ENDPOINT_QUERY = "https://test-payment.momo.vn/v2/gateway/api/query"

    // Bộ thông số tích hợp chuẩn MoMo Payment Gateway v2
    private const val DEFAULT_PARTNER_CODE = "MOMO"
    private const val DEFAULT_ACCESS_KEY = "F8BBA842ECF85"
    private const val DEFAULT_SECRET_KEY = "K951B6PE1waDMi640xX08PD3vg6EkVlz"

    data class MomoCreateResponse(
        val isSuccess: Boolean,
        val momoOrderId: String,
        val requestId: String,
        val qrCodeData: String,
        val payUrl: String,
        val deeplink: String,
        val resultCode: Int,
        val message: String
    )

    data class MomoQueryResponse(
        val isPaid: Boolean,
        val isPending: Boolean,
        val resultCode: Int,
        val transId: String,
        val message: String
    )

    /**
     * Gọi API POST /v2/gateway/api/create để khởi tạo giao dịch MoMo và lấy mã QR / link thanh toán
     */
    suspend fun createPayment(
        appOrderId: String,
        amount: Long,
        momoPhone: String,
        momoHolderName: String,
        transferSyntax: String
    ): MomoCreateResponse = withContext(Dispatchers.IO) {
        val safeAmount = amount.coerceIn(1000L, 50_000_000L)
        val timestamp = System.currentTimeMillis()
        val shortCode = if (appOrderId.length > 6) appOrderId.takeLast(6).uppercase() else appOrderId.uppercase().ifBlank { "OISHI" }
        val momoOrderId = "OISHI_${shortCode}_$timestamp"
        val requestId = "REQ_${shortCode}_$timestamp"
        val orderInfo = "$transferSyntax - Ví nhận: $momoPhone ($momoHolderName)"
        val redirectUrl = "https://webhook.site/b3088a6a-2d17-4f8d-a383-71389a6bc600"
        val ipnUrl = "https://webhook.site/b3088a6a-2d17-4f8d-a383-71389a6bc600"
        val extraData = ""
        val requestType = "payWithMethod"

        val rawSignature = "accessKey=$DEFAULT_ACCESS_KEY" +
            "&amount=$safeAmount" +
            "&extraData=$extraData" +
            "&ipnUrl=$ipnUrl" +
            "&orderId=$momoOrderId" +
            "&orderInfo=$orderInfo" +
            "&partnerCode=$DEFAULT_PARTNER_CODE" +
            "&redirectUrl=$redirectUrl" +
            "&requestId=$requestId" +
            "&requestType=$requestType"

        val signature = hmacSha256(rawSignature, DEFAULT_SECRET_KEY)

        val requestBody = JSONObject().apply {
            put("partnerCode", DEFAULT_PARTNER_CODE)
            put("partnerName", "Oishi Food")
            put("storeId", momoPhone.ifBlank { "OishiStore" })
            put("requestId", requestId)
            put("amount", safeAmount)
            put("orderId", momoOrderId)
            put("orderInfo", orderInfo)
            put("redirectUrl", redirectUrl)
            put("ipnUrl", ipnUrl)
            put("lang", "vi")
            put("extraData", extraData)
            put("requestType", requestType)
            put("signature", signature)
        }

        try {
            val responseStr = postJson(ENDPOINT_CREATE, requestBody.toString())
            val json = JSONObject(responseStr)
            val resultCode = json.optInt("resultCode", -1)
            val message = json.optString("message", "")
            val payUrl = json.optString("payUrl", "")
            val qrCodeUrl = json.optString("qrCodeUrl", "")
            val deeplink = json.optString("deeplink", "")

            // Nhúng trực tiếp payUrl (https://test-payment.momo.vn/v2/gateway/pay?t=...) vào mã QR
            // để quét bằng Camera / Zalo trên máy thật mở thẳng Cổng Thanh Toán MoMo mà không bị lỗi 1005
            val qrPayload = if (payUrl.isNotBlank()) {
                payUrl
            } else {
                QRCodeUtils.buildMomoQrPayload(momoPhone, momoHolderName, safeAmount, transferSyntax)
            }

            MomoCreateResponse(
                isSuccess = resultCode == 0,
                momoOrderId = momoOrderId,
                requestId = requestId,
                qrCodeData = qrPayload,
                payUrl = payUrl,
                deeplink = deeplink,
                resultCode = resultCode,
                message = message
            )
        } catch (e: Exception) {
            MomoCreateResponse(
                isSuccess = false,
                momoOrderId = momoOrderId,
                requestId = requestId,
                qrCodeData = QRCodeUtils.buildMomoQrPayload(momoPhone, momoHolderName, safeAmount, transferSyntax),
                payUrl = "",
                deeplink = "",
                resultCode = -1,
                message = e.message ?: "Lỗi kết nối API MoMo"
            )
        }
    }

    /**
     * Gọi API POST /v2/gateway/api/query để kiểm tra xem khách đã thanh toán đơn hàng trên MoMo hay chưa.
     * - resultCode == 0: Khách ĐÃ thanh toán thành công (isPaid = true)
     * - resultCode == 1000 / 7000 / 9000: Giao dịch đang chờ khách thanh toán (isPaid = false, isPending = true)
     */
    suspend fun queryPaymentStatus(
        momoOrderId: String,
        requestId: String
    ): MomoQueryResponse = withContext(Dispatchers.IO) {
        if (momoOrderId.isBlank() || requestId.isBlank()) {
            return@withContext MomoQueryResponse(
                isPaid = false,
                isPending = false,
                resultCode = -1,
                transId = "",
                message = "Chưa có mã giao dịch MoMo"
            )
        }

        val queryRequestId = "QRY_${System.currentTimeMillis()}"
        val rawSignature = "accessKey=$DEFAULT_ACCESS_KEY" +
            "&orderId=$momoOrderId" +
            "&partnerCode=$DEFAULT_PARTNER_CODE" +
            "&requestId=$queryRequestId"

        val signature = hmacSha256(rawSignature, DEFAULT_SECRET_KEY)

        val requestBody = JSONObject().apply {
            put("partnerCode", DEFAULT_PARTNER_CODE)
            put("requestId", queryRequestId)
            put("orderId", momoOrderId)
            put("lang", "vi")
            put("signature", signature)
        }

        try {
            val responseStr = postJson(ENDPOINT_QUERY, requestBody.toString())
            val json = JSONObject(responseStr)
            val resultCode = json.optInt("resultCode", -1)
            val transId = json.optString("transId", "")
            val message = json.optString("message", "")

            MomoQueryResponse(
                isPaid = (resultCode == 0),
                isPending = (resultCode == 1000 || resultCode == 7000 || resultCode == 9000),
                resultCode = resultCode,
                transId = transId,
                message = message
            )
        } catch (e: Exception) {
            MomoQueryResponse(
                isPaid = false,
                isPending = true,
                resultCode = -1,
                transId = "",
                message = e.message ?: "Đang kiểm tra kết nối API MoMo"
            )
        }
    }

    private fun postJson(endpoint: String, jsonBody: String): String {
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(jsonBody)
            writer.flush()
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun hmacSha256(data: String, secretKey: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
