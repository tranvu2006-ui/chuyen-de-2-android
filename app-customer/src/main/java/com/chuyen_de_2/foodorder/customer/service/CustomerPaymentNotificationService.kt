package com.chuyen_de_2.foodorder.customer.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.google.firebase.database.FirebaseDatabase

/**
 * Tự động đọc thông báo chuyển tiền thành công từ Ví MoMo / Ngân hàng trên thiết bị Khách hàng.
 * Tuyệt đối bỏ qua thông báo nội bộ của app Oishi Food để chỉ xác nhận khi khách thực sự chuyển khoản xong.
 */
class CustomerPaymentNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val pkg = sbn?.packageName.orEmpty().lowercase()

        // Tuyệt đối bỏ qua thông báo của chính app Oishi Food hoặc hệ thống Android
        if (pkg.isBlank() ||
            pkg.startsWith("com.chuyen_de_2") ||
            pkg == "android" ||
            pkg.startsWith("com.android.systemui")
        ) {
            return
        }

        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val fullContent = "$title $text $bigText".uppercase()

        val isFromPaymentApp = pkg.contains("momo") ||
            pkg.contains("bank") ||
            pkg.contains("vcb") ||
            pkg.contains("mbmobile") ||
            pkg.contains("tcb") ||
            pkg.contains("acb") ||
            pkg.contains("bidv") ||
            pkg.contains("icb") ||
            pkg.contains("vpbank") ||
            pkg.contains("tpb") ||
            pkg.contains("messaging") ||
            pkg.contains("mms")

        val hasTransactionKeywords = fullContent.contains("CHUYỂN TIỀN") ||
            fullContent.contains("NHẬN ĐƯỢC") ||
            fullContent.contains("NHẬN TIỀN") ||
            fullContent.contains("GIAO DỊCH THÀNH CÔNG") ||
            fullContent.contains("THANH TOÁN THÀNH CÔNG") ||
            fullContent.contains("BIẾN ĐỘNG SỐ DƯ") ||
            fullContent.contains("SỐ DƯ") ||
            fullContent.contains("+") ||
            fullContent.contains("-")

        if (!isFromPaymentApp && !hasTransactionKeywords) {
            return
        }
        if (!hasTransactionKeywords && !fullContent.contains("OISHI")) {
            return
        }

        val regex = Regex("OISHI[\\s_-]*([A-Z0-9]{4,8})")
        val matchedShortCode = regex.find(fullContent)?.groupValues?.getOrNull(1).orEmpty()

        val ordersRef = FirebaseDatabase.getInstance().getReference("orders")
        ordersRef.get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val orderId = child.key.orEmpty()
                val status = child.child("status").getValue(String::class.java).orEmpty()
                val paymentStatus = child.child("paymentStatus").getValue(String::class.java).orEmpty()
                val paymentMethod = child.child("paymentMethod").getValue(String::class.java).orEmpty()

                if (status.equals(Order.Status.CANCELLED, ignoreCase = true) ||
                    status.equals(Order.Status.COMPLETED, ignoreCase = true)
                ) continue
                if (paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true)) continue
                if (paymentMethod.equals(Order.PaymentMethod.COD, ignoreCase = true)) continue

                val shortCode = orderId.takeLast(6).uppercase()
                val totalPayment = child.child("totalPayment").getValue(Long::class.java)
                    ?: child.child("total").getValue(Long::class.java) ?: 0L

                val isCodeMatch = matchedShortCode.isNotBlank() && shortCode == matchedShortCode
                val isAmountMatch = totalPayment > 0L && hasTransactionKeywords && (
                    fullContent.contains(totalPayment.toString()) ||
                        fullContent.contains(String.format("%,d", totalPayment).replace(",", ".")) ||
                        fullContent.contains(String.format("%,d", totalPayment))
                    )

                if (isCodeMatch || isAmountMatch) {
                    ordersRef.child(orderId).updateChildren(
                        mapOf(
                            "paymentStatus" to Order.PaymentStatus.PAID,
                            "paidAt" to System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }
}
