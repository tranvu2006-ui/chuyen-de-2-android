package com.chuyen_de_2.foodorder.internal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.R
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Dịch vụ chạy ngầm lắng nghe thông báo biến động số dư ngân hàng
 * để tự động xác nhận thanh toán đơn hàng VietQR chuẩn OISHI_******.
 * - Mã đơn: OISHI_****** (với 6 ký tự số hoặc chữ in hoa)
 * - Điều kiện duyệt: Đúng mã đơn hàng VÀ số tiền nhận được >= số tiền trong đơn
 * - Đổi trạng thái: paymentStatus = PAID và status = PAID
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "BankNotifListener"
        private const val CHANNEL_ID = "channel_payment_auto_confirm"

        /** Kiểm tra xem quyền Notification Listener đã được cấp trong Settings chưa */
        fun isPermissionGranted(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat?.contains(packageName) == true
        }

        /** Mở màn hình Settings để người dùng cấp quyền Notification Listener */
        fun openPermissionSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        /** Yêu cầu Android kết nối lại Service nếu đã được cấp quyền */
        fun requestRebindIfNecessary(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isPermissionGranted(context)) {
                try {
                    val component = android.content.ComponentName(context, BankNotificationListenerService::class.java)
                    requestRebind(component)
                    Log.d(TAG, "Đã gọi requestRebind cho BankNotificationListenerService")
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi requestRebind: ${e.message}")
                }
            }
        }

        /** Mở màn hình cài đặt Tự Khởi Chạy (Autostart) trên Xiaomi/MIUI/HyperOS/Vivo */
        fun openAutostartSettings(context: Context) {
            val intents = listOf(
                Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.fromParts("package", context.packageName, null))
            )
            for (intent in intents) {
                try {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "✓✓✓ BankNotificationListenerService ĐÃ KẾT NỐI THÀNH CÔNG VỚI ANDROID! Sẵn sàng lắng nghe thông báo ngân hàng.")
        // Quét ngay các thông báo ngân hàng đang có sẵn trên thanh trạng thái chưa kịp đọc
        try {
            activeNotifications?.forEach { sbn ->
                onNotificationPosted(sbn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi quét thông báo hiện có: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName.orEmpty()

        // Bỏ qua thông báo của chính ứng dụng nội bộ Oishi để tránh vòng lặp tự kích hoạt
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val fullContent = "$title $text $bigText".trim()
        if (fullContent.isBlank()) return

        // Regex bóc tách mã đơn chuẩn OISHI_****** (linh hoạt cả OISHI_XXXXXX, OISHI XXXXXX, OISHIXXXXXX)
        val orderCodeRegex = Regex("""OISHI[_ ]?([A-Z0-9]{6})""", RegexOption.IGNORE_CASE)
        val matchResult = orderCodeRegex.find(fullContent) ?: return
        val rawSuffix = matchResult.groupValues[1].uppercase()
        val orderId = "OISHI_$rawSuffix"

        // Bóc tách số tiền nhận được từ thông báo biến động số dư
        val receivedAmount = extractAmount(fullContent)

        Log.i(TAG, "[$packageName] Phát hiện mã đơn: $orderId | Tiền bóc tách: $receivedAmount VND | Nội dung: $fullContent")

        serviceScope.launch {
            processAutoConfirmPayment(orderId, receivedAmount, packageName)
        }
    }

    /**
     * Bóc tách số tiền chuyển đến từ thông báo ngân hàng
     */
    private fun extractAmount(content: String): Long {
        try {
            // Mẫu 1: Dấu cộng trước số tiền (+50.000 hoặc + 50,000 VND)
            val plusPattern = Regex("""\+\s*([0-9.,]+)\s*(VND|VNĐ|đ|d|dong)?""", RegexOption.IGNORE_CASE)
            val plusMatch = plusPattern.find(content)
            if (plusMatch != null) {
                val digits = plusMatch.groupValues[1].replace(".", "").replace(",", "").trim()
                digits.toLongOrNull()?.let { if (it > 0) return it }
            }

            // Mẫu 2: Số tiền sau các từ khóa "số tiền", "so tien", "tang", "nhan", "GD"
            val keywordPattern = Regex("""(?:số tiền|so tien|tăng|tang|nhận|nhan|gd|thanh toán)[\s:]*\+?\s*([0-9.,]+)""", RegexOption.IGNORE_CASE)
            val keywordMatch = keywordPattern.find(content)
            if (keywordMatch != null) {
                val digits = keywordMatch.groupValues[1].replace(".", "").replace(",", "").trim()
                digits.toLongOrNull()?.let { if (it > 0) return it }
            }

            // Mẫu 3: Số tiền chuẩn có phân cách hàng nghìn (vd: 50.000 đ hoặc 120,000 VND)
            val amountPattern = Regex("""([0-9]{1,3}(?:[.,][0-9]{3})+)\s*(VND|VNĐ|đ|d|dong)""", RegexOption.IGNORE_CASE)
            val amountMatch = amountPattern.find(content)
            if (amountMatch != null) {
                val digits = amountMatch.groupValues[1].replace(".", "").replace(",", "").trim()
                digits.toLongOrNull()?.let { if (it > 0) return it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi bóc tách số tiền: ${e.message}")
        }
        return 0L
    }

    /**
     * Đối soát mã đơn hàng và số tiền chuyển trên Firebase Realtime Database
     * Nếu khớp mã OISHI_****** và số tiền nhận >= số tiền trong đơn -> Đổi trạng thái sang PAID
     */
    private suspend fun processAutoConfirmPayment(orderId: String, receivedAmount: Long, packageName: String) {
        try {
            val db = FirebaseDatabase.getInstance()
            var targetOrderRef = db.getReference("orders").child(orderId)
            var snapshot = targetOrderRef.get().await()

            if (!snapshot.exists()) {
                // Thử tìm linh hoạt cả trường hợp có hoặc không có dấu gạch dưới
                val altId = if (orderId.contains("_")) orderId.replace("_", "") else "OISHI_" + orderId.removePrefix("OISHI")
                val altRef = db.getReference("orders").child(altId)
                val altSnap = altRef.get().await()
                if (altSnap.exists()) {
                    targetOrderRef = altRef
                    snapshot = altSnap
                } else {
                    Log.w(TAG, "Không tìm thấy mã đơn hàng '$orderId' hoặc '$altId' trên hệ thống Firebase")
                    return
                }
            }

            val currentPaymentStatus = snapshot.child("paymentStatus").getValue(String::class.java).orEmpty()
            if (currentPaymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true)) {
                Log.d(TAG, "Đơn hàng '$orderId' đã được xác nhận thanh toán từ trước, bỏ qua")
                return
            }

            val totalPayment = snapshot.child("totalPayment").getValue(Long::class.java)
                ?: snapshot.child("total").getValue(Long::class.java) ?: 0L
            val depositAmount = snapshot.child("depositAmount").getValue(Long::class.java) ?: 0L
            val paymentMode = snapshot.child("paymentMode").getValue(String::class.java).orEmpty()
            val customerName = snapshot.child("customerName").getValue(String::class.java).orEmpty()

            // Xác định số tiền đơn hàng yêu cầu (trả cọc hoặc trả 100%)
            val requiredAmount = if (paymentMode == Order.PaymentMode.DEPOSIT && depositAmount > 0) {
                depositAmount
            } else {
                totalPayment
            }

            // ĐIỀU KIỆN TIÊN QUYẾT: Số tiền nhận được phải LỚN HƠN HOẶC BẰNG số tiền trong đơn
            if (receivedAmount > 0 && receivedAmount < requiredAmount) {
                Log.w(
                    TAG,
                    "Từ chối duyệt đơn '$orderId': Số tiền chuyển (${receivedAmount.toVndCurrency()}) " +
                        "nhỏ hơn số tiền đơn yêu cầu (${requiredAmount.toVndCurrency()})"
                )
                return
            }

            val finalPaidAmount = if (receivedAmount > 0) receivedAmount else requiredAmount

            // Cập nhật trạng thái đơn hàng: status = PAID, paymentStatus = PAID
            val updates = mutableMapOf<String, Any>(
                "status" to Order.Status.PAID,
                "orderStatus" to Order.Status.PAID,
                "paymentStatus" to Order.PaymentStatus.PAID,
                "isPaid" to true,
                "paidAmount" to finalPaidAmount,
                "paidAt" to System.currentTimeMillis(),
                "paymentMethod" to Order.PaymentMethod.BANKING,
                "autoPaidBy" to "BANK_NOTIFICATION_LISTENER",
                "paymentNote" to "Tự động duyệt từ thông báo ngân hàng ($packageName) với số tiền ${finalPaidAmount.toVndCurrency()}"
            )

            targetOrderRef.updateChildren(updates).await()
            Log.i(TAG, "✓ XÁC NHẬN THÀNH CÔNG: Đơn '${targetOrderRef.key}' đã chuyển sang trạng thái PAID!")

            // Phát thông báo âm thanh chuông báo nổi bật cho Quán/Boss
            showSuccessNotification(orderId, finalPaidAmount, packageName, customerName)

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đối soát thanh toán đơn $orderId: ${e.message}", e)
        }
    }

    /**
     * Bắn notification trên máy thông báo tiền đã vào và đơn đã được duyệt
     */
    private fun showSuccessNotification(orderId: String, amount: Long, packageName: String, customer: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelObj = NotificationChannel(
                    CHANNEL_ID,
                    "Tự động duyệt thanh toán",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo khi hệ thống tự động nhận diện tiền chuyển khoản"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channelObj)
            }

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check)
                .setContentTitle("💰 ĐÃ THANH TOÁN: Đơn $orderId")
                .setContentText("Đã nhận ${amount.toVndCurrency()} qua Ngân hàng • Khách: ${customer.ifBlank { "Khách hàng" }}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Đơn hàng $orderId đã tự động chuyển sang trạng thái ĐÃ THANH TOÁN (PAID).\n" +
                        "Số tiền nhận: ${amount.toVndCurrency()}.\n" +
                        "Khách hàng: ${customer.ifBlank { "Khách hàng" }}\n" +
                        "Ứng dụng ghi nhận: $packageName"
                ))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri)
                .setAutoCancel(true)
                .build()

            nm.notify(orderId.hashCode(), notif)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi hiển thị notification: ${e.message}")
        }
    }
}
