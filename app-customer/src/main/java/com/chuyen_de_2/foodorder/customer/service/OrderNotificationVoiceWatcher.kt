package com.chuyen_de_2.foodorder.customer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.ui.tracking.TrackingActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Tên chức năng: Giám Sát Trạng Thái Đơn Hàng Nền & Đọc Thông Báo Tự Động (Background Order Voice Watcher)
 * Cách hoạt động:
 *  - Chạy ngầm trong ứng dụng, lắng nghe thay đổi trạng thái đơn hàng của khách qua WebSocket Realtime Database.
 *  - Khi đơn hàng chuyển sang trạng thái Đang giao (DELIVERING) hoặc Đã đến nơi (ARRIVED_AT_CUSTOMER):
 *      1. Đẩy thông báo Notification lên thanh trạng thái hệ thống Android.
 *      2. Kích hoạt công cụ Text-To-Speech đọc to giọng nói Tiếng Việt ra loa ngoài mà KHÔNG CẦN người dùng phải mở app.
 */
object OrderNotificationVoiceWatcher {

    private var appContext: Context? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingSpeech: String? = null

    // Lưu lại trạng thái trước đó của các đơn hàng để chỉ đọc to khi trạng thái thực sự thay đổi
    private val previousOrderStatuses = ConcurrentHashMap<String, String>()
    private var currentChildListener: ChildEventListener? = null
    private var activeQuery: Query? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        // Khởi tạo công cụ đọc giọng nói Text-To-Speech chạy nền
        initTTS(context.applicationContext)

        // Theo dõi trạng thái đăng nhập của khách hàng
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                startWatchingOrders(uid)
            } else {
                stopWatchingOrders()
            }
        }
    }

    private fun initTTS(context: Context) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeVi = Locale("vi", "VN")
                val result = textToSpeech?.setLanguage(localeVi)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.language = Locale.getDefault()
                }
                isTtsReady = true
                pendingSpeech?.let { message ->
                    textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "ORDER_BACKGROUND_TTS")
                    pendingSpeech = null
                }
            }
        }
    }

    private fun startWatchingOrders(customerId: String) {
        stopWatchingOrders()

        val ordersRef = FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL).getReference("orders")
        val query = ordersRef.orderByChild("customerId").equalTo(customerId)
        activeQuery = query

        currentChildListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val orderId = snapshot.key ?: return
                val status = snapshot.orderStatus()
                // Ghi nhận trạng thái ban đầu để tránh đọc to khi vừa mở app
                previousOrderStatuses[orderId] = status
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val orderId = snapshot.key ?: return
                val newStatus = snapshot.orderStatus()
                val oldStatus = previousOrderStatuses[orderId].orEmpty()

                // Cập nhật trạng thái mới
                previousOrderStatuses[orderId] = newStatus

                // Nếu trạng thái không đổi thì bỏ qua
                if (newStatus == oldStatus) return

                val rawStoreName = snapshot.child("shopName").getValue(String::class.java)
                    ?.ifBlank { snapshot.child("storeName").getValue(String::class.java).orEmpty() }
                    .orEmpty().trim()
                val storeName = if (rawStoreName.isNotBlank()) {
                    if (rawStoreName.startsWith("Quán", ignoreCase = true)) rawStoreName else "Quán $rawStoreName"
                } else "Quán ăn"
                val rawShipperName = snapshot.child("shipperName").getValue(String::class.java).orEmpty()
                val shipperText = if (rawShipperName.isNotBlank() && rawShipperName != "Đang chỉ định tài xế...") "Shipper $rawShipperName" else "Shipper"

                // Kiểm tra các mốc sự kiện quan trọng cần thông báo và đọc to ra loa
                when (newStatus) {
                    Order.Status.PREPARING -> {
                        val title = "🍳 $storeName đã nhận đơn!"
                        val message = "$storeName đã nhận đơn và đang chuẩn bị món ăn."
                        showNotificationAndSpeak(orderId, storeName, title, message)
                    }

                    Order.Status.READY -> {
                        val title = "🍱 $storeName đã nấu xong!"
                        val message = "$storeName đã nấu xong món ăn, đang chờ tài xế nhận đơn."
                        showNotificationAndSpeak(orderId, storeName, title, message)
                    }

                    Order.Status.ASSIGNED -> {
                        val title = "🛵 Shipper đã nhận đơn!"
                        val message = "Shipper đã nhận đơn và đang đến quán lấy món."
                        showNotificationAndSpeak(orderId, storeName, title, message)
                    }

                    Order.Status.ARRIVED_AT_SHOP -> {
                        val title = "🏪 Tài xế đã đến quán!"
                        val message = "$shipperText đã đến quán và đang kiểm tra nhận món."
                        showNotificationAndSpeak(orderId, storeName, title, message)
                    }

                    Order.Status.DELIVERING -> {
                        val title = "🛵 Shipper đang trên đường giao món!"
                        val message = "$shipperText đã lấy món thành công và đang trên đường giao cho bạn. Vui lòng để ý điện thoại; thời gian dự kiến được cập nhật theo vị trí GPS trên màn hình theo dõi đơn."
                        showNotificationAndSpeak(orderId, storeName, title, message)
                    }

                    Order.Status.ARRIVED_AT_CUSTOMER -> {
                        val title = "📍 Shipper đã đến trước cửa nhà bạn!"
                        val message = "$shipperText đã đến trước cửa nhà bạn rồi! Bạn ra nhận món ăn ngay nhé!"
                        showNotificationAndSpeak(orderId, storeName, title, message)
                    }

                    Order.Status.COMPLETED -> {
                        val title = "🎉 Giao hàng thành công!"
                        val message = "Đơn hàng đã giao thành công, chúc bạn ngon miệng!"
                        showNotificationAndSpeak(orderId, storeName, title, message)
                    }

                    Order.Status.CANCELLED -> {
                        val title = "❌ Đơn hàng đã bị huỷ"
                        val message = "Đơn hàng của bạn tại $storeName đã bị huỷ."
                        showNotificationAndSpeak(orderId, storeName, title, message)
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                snapshot.key?.let { previousOrderStatuses.remove(it) }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        query.addChildEventListener(currentChildListener!!)
    }

    private fun DataSnapshot.orderStatus(): String =
        child("status").getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
            ?: child("orderStatus").getValue(String::class.java).orEmpty().uppercase()

    private fun stopWatchingOrders() {
        currentChildListener?.let { listener -> activeQuery?.removeEventListener(listener) }
        currentChildListener = null
        activeQuery = null
        previousOrderStatuses.clear()
    }

    private fun showNotificationAndSpeak(orderId: String, storeName: String, title: String, message: String) {
        val context = appContext ?: return

        // 1. ĐẨY THÔNG BÁO PUSH NOTIFICATION LÊN THANH TRẠNG THÁI ANDROID
        val intent = Intent(context, TrackingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Constants.EXTRA_ORDER_ID, orderId)
            putExtra(Constants.EXTRA_SHOP_NAME, storeName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_ID_ORDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(orderId.hashCode(), notification)

        // 2. PHÁT ÂM THANH TEXT-TO-SPEECH ĐỌC TO NỘI DUNG RA LOA NGOÀI
        speak(message)
    }

    private fun speak(text: String) {
        if (isTtsReady && textToSpeech != null) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ORDER_BACKGROUND_TTS")
        } else {
            pendingSpeech = text
        }
    }
}
