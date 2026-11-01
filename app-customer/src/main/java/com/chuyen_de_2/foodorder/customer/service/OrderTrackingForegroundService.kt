package com.chuyen_de_2.foodorder.customer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.data.model.TrackingLocation
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.ui.tracking.TrackingActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Tên chức năng: Dịch Vụ Theo Dõi Đơn Hàng Nền Liên Tục (Order Tracking Foreground Service)
 *
 * Vấn đề giải quyết:
 *  - Theo dõi Firebase trong Foreground Service để tiếp tục nhận trạng thái khi app ở nền.
 *
 * Giải pháp:
 *  - Foreground Service được Android ưu tiên giữ sống, KHÔNG bị kill khi user tắt app thông thường.
 *  - Service hiển thị notification nhỏ cố định trên thanh trạng thái ("Đang theo dõi đơn hàng...").
 *  - Bên trong duy trì Firebase Realtime DB listener lắng nghe thay đổi trạng thái đơn hàng.
 *  - Khi DELIVERING → đẩy notification "Shipper sắp đến" + đọc loa.
 *  - Khi ARRIVED_AT_CUSTOMER → đẩy notification "Shipper đã đến" + đọc loa.
 *
 * Vòng đời:
 *  - Khởi động: FoodOrderCustomerApp.onCreate() gọi startForegroundService().
 *  - Dừng tự động: Khi user đăng xuất (FirebaseAuth listener phát hiện uid = null).
 */
class OrderTrackingForegroundService : Service() {

    // ID thông báo cố định trên thanh trạng thái (Foreground notification bắt buộc phải có)
    private val FOREGROUND_NOTIF_ID = 9999
    // ID channel riêng cho thông báo "đang theo dõi" — dùng IMPORTANCE_LOW để không tạo tiếng động
    private val TRACKING_CHANNEL_ID = "channel_tracking_silent"
    private val TRACKING_CHANNEL_NAME = "Theo dõi đơn hàng"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Engine Text-To-Speech đọc thông báo bằng giọng nói tiếng Việt
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Lưu trạng thái cũ của từng đơn để chỉ đọc khi trạng thái THỰC SỰ thay đổi
    private val previousStatuses = ConcurrentHashMap<String, String>()

    // Firebase Realtime Database listener đang hoạt động
    private var activeListener: ChildEventListener? = null
    private var activeQuery: Query? = null

    override fun onCreate() {
        super.onCreate()
        createSilentChannel()
        initTts()
        startForeground(FOREGROUND_NOTIF_ID, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val customerId = FirebaseAuth.getInstance().currentUser?.uid
        if (customerId.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startListening(customerId)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // =========================================================================
    // FIREBASE REALTIME DATABASE LISTENER
    // =========================================================================

    private fun startListening(customerId: String) {
        // Tránh đăng ký listener trùng lặp
        stopListening()

        val ordersRef = FirebaseDatabase
            .getInstance(Constants.FIREBASE_DATABASE_URL)
            .getReference("orders")
            .orderByChild("customerId")
            .equalTo(customerId)
        activeQuery = ordersRef

        activeListener = object : ChildEventListener {

            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Ghi nhận trạng thái ban đầu của đơn để tránh đọc lại khi service khởi động
                val orderId = snapshot.key ?: return
                val status = snapshot.orderStatus()
                previousStatuses[orderId] = status
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val orderId = snapshot.key ?: return
                val newStatus = snapshot.orderStatus()
                val oldStatus = previousStatuses[orderId].orEmpty()

                // Cập nhật trạng thái mới vào bộ nhớ
                previousStatuses[orderId] = newStatus

                // Bỏ qua nếu không thay đổi hoặc chưa có trạng thái cũ (mới add)
                if (newStatus == oldStatus || oldStatus.isBlank()) return

                val order = snapshot.getValue(Order::class.java) ?: return
                val rawStoreName = snapshot.child("shopName").getValue(String::class.java)
                    .orEmpty().ifBlank { snapshot.child("storeName").getValue(String::class.java).orEmpty() }.trim()
                val storeName = if (rawStoreName.isNotBlank()) {
                    if (rawStoreName.startsWith("Quán", ignoreCase = true)) rawStoreName else "Quán $rawStoreName"
                } else "Quán ăn"
                val shipperName = snapshot.child("shipperName").getValue(String::class.java)
                    .orEmpty().takeUnless { it.isBlank() || it == "Đang chỉ định tài xế..." }
                val shipper = shipperName?.let { "Shipper $it" } ?: "Shipper"

                serviceScope.launch {
                    val notification = when (newStatus) {
                        Order.Status.PREPARING ->
                            "🍳 $storeName đã nhận đơn!" to "$storeName đã nhận đơn và đang chuẩn bị món ăn."
                        Order.Status.READY ->
                            "🍱 $storeName đã nấu xong!" to "$storeName đã chuẩn bị xong món, đang chờ shipper đến lấy."
                        Order.Status.ASSIGNED ->
                            "🛵 Shipper đã nhận đơn!" to "Shipper đã nhận đơn và đang đến quán lấy món."
                        Order.Status.ARRIVED_AT_SHOP ->
                            "🏪 Shipper đã đến quán!" to "$shipper đã đến quán $storeName và đang nhận món."
                        Order.Status.DELIVERING -> {
                            val eta = estimateMinutes(orderId, order)
                            val etaMins = eta ?: 20
                            val message = "$shipper đang trên đường giao đến nhà bạn. Dự kiến còn khoảng $etaMins phút nữa sẽ đến. Thời gian có thể thay đổi theo tình hình giao thông. Vui lòng để ý điện thoại nhé!"
                            "🛵 Shipper đang trên đường giao đến bạn!" to message
                        }
                        Order.Status.ARRIVED_AT_CUSTOMER ->
                            "📍 Shipper đã đến trước cửa nhà bạn!" to "$shipper đã đến nơi rồi, bạn vui lòng ra nhận món nhé!"
                        Order.Status.COMPLETED ->
                            "🎉 Giao hàng thành công!" to "Đơn hàng đã được giao thành công, chúc bạn ngon miệng!"
                        Order.Status.CANCELLED ->
                            "❌ Đơn hàng đã bị hủy" to "Đơn hàng của bạn tại $storeName đã bị hủy."
                        else -> null
                    } ?: return@launch

                    // Cập nhật ngay nội dung của Notification bar cố định (ghim liên tục trên thanh trạng thái)
                    val updatedPinnedNotif = buildForegroundNotification(notification.first, notification.second, orderId)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(FOREGROUND_NOTIF_ID, updatedPinnedNotif)

                    pushNotificationAndSpeak(orderId, notification.first, notification.second)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                snapshot.key?.let { previousStatuses.remove(it) }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Không thể theo dõi trạng thái đơn hàng", error.toException())
            }
        }

        // Đăng ký listener với Firebase
        ordersRef.addChildEventListener(activeListener!!)
    }

    private fun DataSnapshot.orderStatus(): String =
        child("status").getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
            ?: child("orderStatus").getValue(String::class.java).orEmpty().uppercase()

    private suspend fun estimateMinutes(orderId: String, order: Order): Int? {
        return try {
            if (order.effectiveAddress.isBlank()) return null
            val tracking = FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
                .getReference("tracking")
                .child(orderId)
                .get()
                .await()
                .getValue(TrackingLocation::class.java)
                ?: return null
            if (tracking.lat == 0.0 || tracking.lng == 0.0) return null
            if (tracking.updatedAt > 0 && System.currentTimeMillis() - tracking.updatedAt > 5 * 60 * 1000) return null

            val destination = GoogleMapUtils.geocodeAddress(this, order.effectiveAddress)
            if (destination.latitude == 0.0 && destination.longitude == 0.0) return null
            val distanceKm = GoogleMapUtils.calculateDistanceKm(
                LatLng(tracking.lat, tracking.lng),
                destination
            )
            kotlin.math.ceil(distanceKm * 2.5 + 3.0).toInt().coerceIn(3, 45)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Không tính được ETA cho đơn $orderId", error)
            null
        }
    }

    private fun stopListening() {
        activeListener?.let { listener -> activeQuery?.removeEventListener(listener) }
        activeListener = null
        activeQuery = null
        previousStatuses.clear()
    }

    // =========================================================================
    // NOTIFICATION + TEXT-TO-SPEECH
    // =========================================================================

    /**
     * Đẩy Heads-Up Notification lên màn hình (hiện ngay cả khi điện thoại khóa màn hình)
     * và đọc to nội dung bằng giọng nói tiếng Việt.
     */
    private fun pushNotificationAndSpeak(orderId: String, title: String, message: String) {
        // Intent: Bấm vào notification → mở TrackingActivity xem đơn hàng
        val tapIntent = Intent(this, TrackingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Constants.EXTRA_ORDER_ID, orderId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            orderId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Xây dựng Notification với PRIORITY_MAX để hiện Heads-Up (popup nổi trên màn hình)
        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_ORDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            // BigTextStyle: kéo thông báo ra sẽ thấy đủ nội dung
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // DEFAULT_ALL: bật cả âm thanh chuông + rung để alert người dùng
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Dùng orderId.hashCode() làm ID để mỗi đơn có 1 notification riêng
        manager.notify(orderId.hashCode(), notification)

        // Đọc to nội dung qua loa ngoài sau khi đã push notification
        speak(message)
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Thiết lập ngôn ngữ đọc là tiếng Việt
                val localeVi = Locale("vi", "VN")
                val result = tts?.setLanguage(localeVi)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback về ngôn ngữ mặc định của máy nếu thiếu gói tiếng Việt
                    tts?.language = Locale.getDefault()
                }
                isTtsReady = true
            }
        }
    }

    private fun speak(text: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FOREGROUND_TTS")
        }
    }

    // =========================================================================
    // FOREGROUND NOTIFICATION (bắt buộc để Foreground Service hoạt động)
    // =========================================================================

    /**
     * Notification "silent" cố định trên thanh trạng thái.
     * Người dùng thấy icon nhỏ ở trên cùng — đây là yêu cầu bắt buộc của Android.
     * Dùng IMPORTANCE_LOW để không phát tiếng/rung khi service start.
     */
    private fun buildForegroundNotification(
        title: String = "Oishi Food — Đơn hàng đang được xử lý",
        content: String = "Bấm để xem chi tiết và theo dõi tài xế",
        orderId: String? = null
    ): android.app.Notification {
        val tapIntent = if (!orderId.isNullOrBlank()) {
            Intent(this, TrackingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Constants.EXTRA_ORDER_ID, orderId)
            }
        } else {
            Intent(this, TrackingActivity::class.java)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            FOREGROUND_NOTIF_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createSilentChannel() {
        val channel = android.app.NotificationChannel(
            TRACKING_CHANNEL_ID,
            TRACKING_CHANNEL_NAME,
            android.app.NotificationManager.IMPORTANCE_LOW   // LOW = không tiếng, không rung
        ).apply {
            description = "Thông báo nền theo dõi trạng thái đơn hàng"
            setShowBadge(false)  // Không hiện badge số trên icon app
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopListening()
        serviceScope.cancel()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OrderTrackingService"
    }
}
