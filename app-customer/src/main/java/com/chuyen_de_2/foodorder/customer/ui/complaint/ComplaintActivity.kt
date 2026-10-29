package com.chuyen_de_2.foodorder.customer.ui.complaint

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import java.io.ByteArrayOutputStream
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityComplaintBinding
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trung tâm gửi khiếu nại đơn hàng chuẩn b12.html
 * Gửi lên Firebase Realtime Database node `complaints/{complaintId}` cho Admin duyệt
 */
/**
 * Tên chức năng: Trung Tâm Gửi Khiếu Nại & Tranh Chấp Đơn Hàng (Order Complaint & Dispute Center)
 * Cách hoạt động:
 *  - Thu thập mã đơn hàng, lý do khiếu nại (Giao sai món, đồ ăn nguội/bẩn, tài xế thái độ xấu, giao quá trễ, tính sai tiền, v.v.).
 *  - Cho phép đính kèm mô tả chi tiết và liên kết hình ảnh bằng chứng (Evidence Image URL).
 *  - Đẩy khiếu nại lên Firebase Realtime Database / Firestore node complaints/{complaintId} với trạng thái PENDING.
 *  - Thông tin sẽ được gửi tới Admin Portal để xem xét hoàn tiền hoặc xử phạt vi phạm.
 * Luồng hướng tới:
 *  - Gửi thành công -> Đóng màn hình quay về [OrderHistoryActivity] hoặc [TrackingActivity].
 */
@AndroidEntryPoint
class ComplaintActivity : AppCompatActivity() {
    private var selectedEvidenceBase64: String = ""

    private val pickEvidenceLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val base64 = uriToBase64(uri)
        if (!base64.isNullOrBlank()) {
            selectedEvidenceBase64 = base64
            binding.ivEvidencePreview.clearColorFilter()
            Glide.with(this).load(base64).into(binding.ivEvidencePreview)
            binding.tvEvidenceStatus.text = "✓ Đã chọn ảnh bằng chứng từ thiết bị"
            binding.tvEvidenceStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
        }
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            val scaledBitmap = Bitmap.createScaledBitmap(
                originalBitmap,
                500,
                (500 * (originalBitmap.height.toFloat() / originalBitmap.width)).toInt(),
                true
            )
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private lateinit var binding: ActivityComplaintBinding

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private val reasons = arrayOf(
        "Giao sai món ăn / Thiếu món",
        "Đồ ăn nguội lạnh / Không đảm bảo vệ sinh",
        "Tài xế giao hàng có thái độ không phù hợp",
        "Giao hàng quá trễ so với cam kết (>45 phút)",
        "Quán tính sai tiền hoặc không áp dụng ưu đãi",
        "Lý do khác"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComplaintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyToolbarInsets(binding.toolbar)

        setupToolbar()
        setupSpinner()
        handleIntentData()
        setupSubmitButton()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, reasons)
        binding.spinnerReason.adapter = adapter
    }

    private fun handleIntentData() {
        val orderId = intent.getStringExtra(Constants.EXTRA_ORDER_ID)
        if (!orderId.isNullOrBlank()) {
            binding.etOrderId.setText(orderId)
        }
    }

    private fun setupSubmitButton() {
        binding.btnPickEvidenceImage.setOnClickListener {
            pickEvidenceLauncher.launch("image/*")
        }
        binding.btnClearEvidenceImage.setOnClickListener {
            selectedEvidenceBase64 = ""
            Glide.with(this).clear(binding.ivEvidencePreview)
            binding.ivEvidencePreview.setImageResource(R.drawable.ic_camera)
            binding.ivEvidencePreview.setColorFilter(ContextCompat.getColor(this, R.color.text_muted))
            binding.tvEvidenceStatus.text = "Chỉ chọn ảnh trực tiếp từ thư viện điện thoại"
            binding.tvEvidenceStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        binding.btnSubmitComplaint.setOnClickListener {
            val orderId = binding.etOrderId.text.toString().trim()
            val description = binding.etDescription.text.toString().trim()
            val evidenceUrl = selectedEvidenceBase64
            val selectedReason = binding.spinnerReason.selectedItem.toString()

            if (orderId.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập mã đơn hàng cần khiếu nại", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (description.isBlank()) {
                Toast.makeText(this, "Vui lòng mô tả chi tiết sự việc", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val customerId = firebaseAuth.currentUser?.uid ?: "anonymous_customer"
            val storeId = intent.getStringExtra("EXTRA_STORE_ID") ?: ""

            binding.btnSubmitComplaint.isEnabled = false
            binding.btnSubmitComplaint.text = "Đang gửi khiếu nại..."

            lifecycleScope.launch {
                var resolvedStoreId = storeId
                if (resolvedStoreId.isBlank()) {
                    val order = firebaseRepository.getOrder(orderId)
                    if (order != null) {
                        resolvedStoreId = order.shopId.ifBlank { order.storeId }
                    }
                }

                val complaint = Complaint(
                    orderId = orderId,
                    customerId = customerId,
                    storeId = resolvedStoreId,
                    reason = "[$selectedReason] $description",
                    evidenceImage = evidenceUrl,
                    status = Complaint.ComplaintStatus.PENDING,
                    createdAt = System.currentTimeMillis()
                )

                val result = firebaseRepository.submitComplaint(complaint)
                if (result.isSuccess) {
                    Toast.makeText(
                        this@ComplaintActivity,
                        "🎉 Đã gửi khiếu nại thành công! Admin sẽ xử lý trong 24h.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    binding.btnSubmitComplaint.isEnabled = true
                    binding.btnSubmitComplaint.text = "Gửi Khiếu Nại Lên Hệ Thống ⚖️"
                    Toast.makeText(
                        this@ComplaintActivity,
                        "Lỗi khi gửi: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
