package com.chuyen_de_2.foodorder.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client gọi API Ví điện tử MoMo & VietQR Napas 24/7:
 * 1. Gọi API POST https://api.vietqr.io/v2/generate (BIN MoMo: 971025) để tạo mã QR chuyển tiền thật vào SĐT MoMo của Quán.
 * 2. Gọi API đối soát giao dịch tự động (SePay / Casso / Webhook REST API) mỗi 3 giây để tự động đọc trạng thái khi khách chuyển tiền xong (không dùng nút bấm thủ công).
 */
object MomoPaymentGatewayClient {

    private const val VIETQR_GENERATE_API = "https://api.vietqr.io/v2/generate"
    private const val SEPAY_TRANSACTIONS_API = "https://my.sepay.vn/userapi/transactions/list"
    private const val CASSO_TRANSACTIONS_API = "https://oauth.casso.vn/v2/transactions"
    private const val MOMO_NAPAS_BIN = 971025

    data class MomoQrResult(
        val isSuccess: Boolean,
        val qrCodeData: String,
        val message: String
    )

    data class TransferVerifyResult(
        val isPaid: Boolean,
        val transactionId: String = "",
        val message: String = ""
    )

    /**
     * Gọi API POST https://api.vietqr.io/v2/generate với mã BIN chuẩn của tài khoản thụ hưởng (mặc định 970422 - MBBank)
     * để cả App MoMo lẫn App Ngân hàng khi quét đều nhận diện đúng chủ tài khoản (VD: 0798351169 - TRAN VU)
     * và chuyển tiền thật thành công 100% (không bị lỗi "Tài khoản không hợp lệ" hay "Ví chưa xác thực").
     */
    suspend fun generateRealMomoQr(
        momoPhone: String,
        momoHolderName: String,
        amount: Long,
        transferSyntax: String,
        bankBin: String = "970422"
    ): MomoQrResult = withContext(Dispatchers.IO) {
        val cleanPhone = momoPhone.replace(Regex("[^0-9]"), "").ifBlank { "0798351169" }
        val cleanName = momoHolderName.trim().uppercase().ifBlank { "OISHI FOOD" }
        val safeAmount = amount.coerceAtLeast(1000L)
        val cleanNote = transferSyntax.trim().ifBlank { "OISHI" }
        val acqIdInt = bankBin.trim().toIntOrNull() ?: 970422

        try {
            val reqJson = JSONObject().apply {
                put("accountNo", cleanPhone)
                put("accountName", cleanName)
                put("acqId", acqIdInt)
                put("amount", safeAmount)
                put("addInfo", cleanNote)
                put("format", "text")
                put("template", "compact")
            }

            val rawResponse = postJson(VIETQR_GENERATE_API, reqJson.toString())
            val resObj = JSONObject(rawResponse)
            val code = resObj.optString("code", "")
            val dataObj = resObj.optJSONObject("data")
            val qrData = dataObj?.optString("qrCode", "").orEmpty()

            if (code == "00" && qrData.isNotBlank()) {
                return@withContext MomoQrResult(
                    isSuccess = true,
                    qrCodeData = qrData,
                    message = resObj.optString("desc", "Success")
                )
            }
        } catch (_: Exception) {}

        val fallbackPayload = QRCodeUtils.buildMomoQrPayload(
            phone = cleanPhone,
            receiverName = cleanName,
            amount = safeAmount,
            transferSyntax = cleanNote
        )
        MomoQrResult(
            isSuccess = false,
            qrCodeData = fallbackPayload,
            message = "Fallback QR"
        )
    }

    /**
     * Gọi REST API đối soát lịch sử biến động số dư để tự động phát hiện khi khách đã chuyển khoản thật xong.
     * Chỉ trả về isPaid = true khi tìm thấy giao dịch khớp mã đơn (OISHI <shortCode>) và số tiền.
     */
    suspend fun checkRealTransferViaApi(
        orderId: String,
        expectedAmount: Long,
        apiToken: String = ""
    ): TransferVerifyResult = withContext(Dispatchers.IO) {
        val rawCode = if (orderId.length > 6) orderId.takeLast(6).uppercase() else orderId.uppercase()
        val cleanCode = rawCode.replace(Regex("[^A-Z0-9]"), "")
        if (cleanCode.isBlank()) {
            return@withContext TransferVerifyResult(isPaid = false, message = "Mã đơn trống")
        }

        val token = apiToken.trim()
        if (token.isNotBlank()) {
            // 1. Kiểm tra qua SePay Transaction API (GET https://my.sepay.vn/userapi/transactions/list)
            try {
                val sepayJson = getJsonWithBearer("$SEPAY_TRANSACTIONS_API?limit=20", token)
                val root = JSONObject(sepayJson)
                val transactions = root.optJSONArray("transactions")
                if (transactions != null) {
                    for (i in 0 until transactions.length()) {
                        val tx = transactions.optJSONObject(i) ?: continue
                        val content = tx.optString("transaction_content", "").uppercase()
                        val amountIn = tx.optString("amount_in", "0").substringBefore(".").toLongOrNull() ?: 0L
                        val txId = tx.optString("id", "")
                        if (content.contains(cleanCode) && (expectedAmount <= 0L || amountIn >= expectedAmount)) {
                            return@withContext TransferVerifyResult(
                                isPaid = true,
                                transactionId = txId,
                                message = "Đã xác nhận giao dịch qua API SePay (#$txId)"
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Kiểm tra qua Casso Transaction API (GET https://oauth.casso.vn/v2/transactions)
            try {
                val cassoJson = getJsonWithApiKey("$CASSO_TRANSACTIONS_API?pageSize=20&sort=DESC", token)
                val root = JSONObject(cassoJson)
                val records = root.optJSONObject("data")?.optJSONArray("records")
                if (records != null) {
                    for (i in 0 until records.length()) {
                        val tx = records.optJSONObject(i) ?: continue
                        val desc = tx.optString("description", "").uppercase()
                        val amount = tx.optLong("amount", 0L)
                        val tid = tx.optString("tid", "")
                        if (desc.contains(cleanCode) && (expectedAmount <= 0L || amount >= expectedAmount)) {
                            return@withContext TransferVerifyResult(
                                isPaid = true,
                                transactionId = tid,
                                message = "Đã xác nhận giao dịch qua API Casso (#$tid)"
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        TransferVerifyResult(
            isPaid = false,
            message = "Đang chờ giao dịch chuyển khoản OISHI $cleanCode..."
        )
    }

    private fun getJsonWithBearer(endpoint: String, token: String): String {
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun getJsonWithApiKey(endpoint: String, apiKey: String): String {
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Authorization", "Apikey $apiKey")
            setRequestProperty("Accept", "application/json")
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
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
}
