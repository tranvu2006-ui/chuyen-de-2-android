package com.chuyen_de_2.foodorder.internal.ui.shop

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chuyen_de_2.foodorder.core.util.QRCodeUtils
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.DialogStoreChatCustomerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatCustomerBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogStoreChatCustomerBinding? = null
    private val binding get() = _binding!!

    private var customerName: String = "Khách hàng"
    private var orderId: String = ""
    private var orderNote: String = ""

    private val seenMessageKeys = mutableSetOf<String>()
    private var primaryRef: DatabaseReference? = null
    private var fallbackRef: DatabaseReference? = null
    private var chatListener: ChildEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customerName = arguments?.getString(ARG_CUSTOMER_NAME)?.ifBlank { "Khách hàng" } ?: "Khách hàng"
        orderId = arguments?.getString(ARG_ORDER_ID) ?: ""
        orderNote = arguments?.getString(ARG_ORDER_NOTE) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogStoreChatCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val shortCode = if (orderId.isNotBlank()) {
            "#ORD_OISHI_${QRCodeUtils.getShortOrderCode(orderId)}"
        } else ""

        binding.tvChatCustomerName.text = "Khách: $customerName"
        binding.tvChatOrderSub.text = if (shortCode.isNotBlank()) {
            "$shortCode • Trực tuyến"
        } else {
            "Kênh chat đơn hàng trực tiếp"
        }

        if (orderNote.isNotBlank()) {
            binding.layoutInitialNote.visibility = View.VISIBLE
            binding.tvInitialCustomerNote.text = "📝 Ghi chú của khách: \"$orderNote\""
        } else {
            binding.layoutInitialNote.visibility = View.GONE
        }

        binding.btnCloseChat.setOnClickListener {
            dismiss()
        }

        binding.chipPreparing.setOnClickListener {
            sendMessage("🍳 Dạ quán đang chuẩn bị món cho bạn, bạn đợi quán ít phút nhé!")
        }
        binding.chipReady.setOnClickListener {
            sendMessage("🛵 Món của bạn đã hoàn tất, tài xế đang trên đường giao đến bạn ạ!")
        }
        binding.chipOutOfStock.setOnClickListener {
            sendMessage("🙏 Dạ món này hiện quán vừa hết, bạn đổi sang món tương tự giúp quán nhé?")
        }
        binding.chipThankYou.setOnClickListener {
            sendMessage("❤️ Cảm ơn bạn đã đặt món và ủng hộ quán! Chúc bạn ngon miệng ạ!")
        }

        binding.btnSendChat.setOnClickListener {
            sendFromInput()
        }

        binding.etChatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInput()
                true
            } else {
                false
            }
        }

        if (orderId.isNotBlank()) {
            val db = FirebaseDatabase.getInstance()
            val listener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                    val key = snapshot.key ?: return
                    if (!seenMessageKeys.add(key)) return
                    val sender = snapshot.child("sender").getValue(String::class.java) ?: "customer"
                    val text = snapshot.child("text").getValue(String::class.java) ?: return
                    val time = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
                    if (sender == "customer") {
                        addCustomerMessage(text, timeStr)
                    } else {
                        addShopMessage(text, timeStr)
                    }
                }
                override fun onChildChanged(snapshot: DataSnapshot, prev: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, prev: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            }
            chatListener = listener
            primaryRef = db.getReference("tracking").child("chats_$orderId").also {
                it.addChildEventListener(listener)
            }
            fallbackRef = db.getReference("order_chats").child(orderId).also {
                it.addChildEventListener(listener)
            }
        }
    }

    private fun sendFromInput() {
        val text = binding.etChatInput.text.toString().trim()
        if (text.isNotBlank()) {
            sendMessage(text)
            binding.etChatInput.setText("")
        }
    }

    private fun sendMessage(content: String) {
        val now = System.currentTimeMillis()
        if (orderId.isNotBlank()) {
            val db = FirebaseDatabase.getInstance()
            val msgKey = db.getReference("tracking").child("chats_$orderId").push().key
                ?: now.toString()
            val payload = mapOf(
                "sender" to "shop",
                "text" to content,
                "timestamp" to now
            )
            // Ghi vào cả tracking/chats_{orderId} (luôn được cấp quyền trong rules) và order_chats/{orderId}
            db.getReference("tracking").child("chats_$orderId").child(msgKey).setValue(payload)
            db.getReference("order_chats").child(orderId).child(msgKey).setValue(payload)
        } else {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
            addShopMessage(content, timeStr)
        }
    }

    /** Tin nhắn của Quán gửi đi (nằm bên phải, nền xanh) */
    private fun addShopMessage(content: String, timeStr: String) {
        val context = context ?: return
        val binding = _binding ?: return
        val shopMsgLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_chat_bubble_user)
            val padding10 = (10 * resources.displayMetrics.density).toInt()
            setPadding(padding10, padding10, padding10, padding10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                marginStart = (40 * resources.displayMetrics.density).toInt()
                bottomMargin = (10 * resources.displayMetrics.density).toInt()
            }
        }

        val tvContent = TextView(context).apply {
            text = content
            setTextColor(ContextCompat.getColor(context, R.color.white))
            textSize = 12f
            setLineSpacing(3f * resources.displayMetrics.density, 1f)
        }

        val tvTime = TextView(context).apply {
            text = "Quán • $timeStr"
            setTextColor(ContextCompat.getColor(context, R.color.border))
            textSize = 10f
            val topMargin = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, 0)
            gravity = Gravity.END
        }

        shopMsgLayout.addView(tvContent)
        shopMsgLayout.addView(tvTime)
        binding.llChatContainer.addView(shopMsgLayout)

        binding.scrollChatMessages.post {
            _binding?.scrollChatMessages?.fullScroll(View.FOCUS_DOWN)
        }
    }

    /** Tin nhắn của Khách gửi tới (nằm bên trái, nền trắng) */
    private fun addCustomerMessage(content: String, timeStr: String) {
        val context = context ?: return
        val binding = _binding ?: return
        val custMsgLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_chat_bubble_shop)
            val padding10 = (10 * resources.displayMetrics.density).toInt()
            setPadding(padding10, padding10, padding10, padding10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                marginEnd = (40 * resources.displayMetrics.density).toInt()
                bottomMargin = (10 * resources.displayMetrics.density).toInt()
            }
        }

        val tvContent = TextView(context).apply {
            text = content
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 12f
            setLineSpacing(3f * resources.displayMetrics.density, 1f)
        }

        val tvTime = TextView(context).apply {
            text = "$customerName • $timeStr"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 10f
            val topMargin = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, 0)
        }

        custMsgLayout.addView(tvContent)
        custMsgLayout.addView(tvTime)
        binding.llChatContainer.addView(custMsgLayout)

        binding.scrollChatMessages.post {
            _binding?.scrollChatMessages?.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroyView() {
        chatListener?.let { listener ->
            primaryRef?.removeEventListener(listener)
            fallbackRef?.removeEventListener(listener)
        }
        chatListener = null
        primaryRef = null
        fallbackRef = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CUSTOMER_NAME = "customer_name"
        private const val ARG_ORDER_ID = "order_id"
        private const val ARG_ORDER_NOTE = "order_note"

        fun newInstance(
            customerName: String,
            orderId: String,
            orderNote: String = ""
        ): ChatCustomerBottomSheetDialogFragment {
            return ChatCustomerBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CUSTOMER_NAME, customerName)
                    putString(ARG_ORDER_ID, orderId)
                    putString(ARG_ORDER_NOTE, orderNote)
                }
            }
        }
    }
}
