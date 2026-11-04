package com.chuyen_de_2.foodorder.customer.ui.tracking

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.DialogChatStoreBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatStoreBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogChatStoreBinding? = null
    private val binding get() = _binding!!

    private var storeName: String = "Quán ăn"
    private var orderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storeName = arguments?.getString(ARG_STORE_NAME) ?: "Quán ăn"
        orderId = arguments?.getString(ARG_ORDER_ID) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogChatStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvChatShopName.text = storeName
        val orderCode = if (orderId.isNotBlank()) "#${orderId.takeLast(6).uppercase()}" else ""
        binding.tvFirstShopMessage.text =
            "Dạ $storeName xin chào bạn! Đơn $orderCode của bạn đang được quán tiếp nhận. Bạn cần dặn dò thêm về món (đá, đường, ống hút...) cứ nhắn cho quán nhé! ❤️"

        binding.btnCloseChat.setOnClickListener {
            dismiss()
        }

        binding.chipIceSugar.setOnClickListener {
            sendMessage("🥤 Làm ít đá, ít ngọt giúp mình nhé")
        }
        binding.chipStraws.setOnClickListener {
            sendMessage("🥢 Cho mình xin thêm 2 ống hút to")
        }
        binding.chipFast.setOnClickListener {
            sendMessage("⚡ Quán làm nhanh giúp mình nhé")
        }
        binding.chipIceSeparate.setOnClickListener {
            sendMessage("🧊 Để đá riêng giúp mình được không?")
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
            val db = com.google.firebase.database.FirebaseDatabase.getInstance()
            val chatRef = db.getReference("order_chats").child(orderId)
            chatRef.addChildEventListener(object : com.google.firebase.database.ChildEventListener {
                override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, prev: String?) {
                    val sender = snapshot.child("sender").getValue(String::class.java) ?: "customer"
                    val text = snapshot.child("text").getValue(String::class.java) ?: return
                    val time = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
                    if (sender == "customer") {
                        addUserMessage(text, timeStr)
                    } else {
                        addShopReply(text, timeStr)
                    }
                }
                override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, prev: String?) {}
                override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {}
                override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, prev: String?) {}
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
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
        if (orderId.isNotBlank()) {
            val db = com.google.firebase.database.FirebaseDatabase.getInstance()
            val msgKey = db.getReference("order_chats").child(orderId).push().key ?: System.currentTimeMillis().toString()
            db.getReference("order_chats").child(orderId).child(msgKey).setValue(
                mapOf(
                    "sender" to "customer",
                    "text" to content,
                    "timestamp" to System.currentTimeMillis()
                )
            )
            // Cập nhật trực tiếp vào ghi chú của đơn trên Firebase
            db.getReference("orders").child(orderId).child("note").setValue(content)
        } else {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            addUserMessage(content, timeStr)
        }
    }

    private fun addUserMessage(content: String, timeStr: String) {
        val context = context ?: return
        val userMsgLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_chat_bubble_user)
            val padding10 = (10 * resources.displayMetrics.density).toInt()
            setPadding(padding10, padding10, padding10, padding10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.END
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
            text = timeStr
            setTextColor(ContextCompat.getColor(context, R.color.border))
            textSize = 10f
            val topMargin = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, 0)
            gravity = android.view.Gravity.END
        }

        userMsgLayout.addView(tvContent)
        userMsgLayout.addView(tvTime)
        binding.llChatContainer.addView(userMsgLayout)

        binding.scrollChatMessages.post {
            binding.scrollChatMessages.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun addShopReply(content: String, timeStr: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) {
        val context = context ?: return
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val shopMsgLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_chat_bubble_shop)
            val padding10 = (10 * resources.displayMetrics.density).toInt()
            setPadding(padding10, padding10, padding10, padding10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.START
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
            text = timeStr
            setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            textSize = 10f
            val topMargin = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, 0)
        }

        shopMsgLayout.addView(tvContent)
        shopMsgLayout.addView(tvTime)
        binding.llChatContainer.addView(shopMsgLayout)

        binding.scrollChatMessages.post {
            binding.scrollChatMessages.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_STORE_NAME = "store_name"
        private const val ARG_ORDER_ID = "order_id"

        fun newInstance(storeName: String, orderId: String): ChatStoreBottomSheetDialogFragment {
            return ChatStoreBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STORE_NAME, storeName)
                    putString(ARG_ORDER_ID, orderId)
                }
            }
        }
    }
}
