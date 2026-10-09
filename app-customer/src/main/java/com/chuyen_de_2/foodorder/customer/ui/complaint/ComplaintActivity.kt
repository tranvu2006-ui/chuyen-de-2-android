package com.chuyen_de_2.foodorder.customer.ui.complaint

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.databinding.ActivityComplaintBinding
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trung tâm gửi khiếu nại đơn hàng chuẩn b12.html
 * Gửi lên Firebase Realtime Database node `complaints/{complaintId}` cho Admin duyệt
 */
@AndroidEntryPoint
class ComplaintActivity : AppCompatActivity() {

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

        InsetUtils.setLightStatusBar(this, true)
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
        binding.btnSubmitComplaint.setOnClickListener {
            val orderId = binding.etOrderId.text.toString().trim()
            val description = binding.etDescription.text.toString().trim()
            val evidenceUrl = binding.etEvidenceUrl.text.toString().trim()
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

            val complaint = Complaint(
                orderId = orderId,
                customerId = customerId,
                storeId = storeId,
                reason = "[$selectedReason] $description",
                evidenceImage = evidenceUrl,
                status = Complaint.ComplaintStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )

            binding.btnSubmitComplaint.isEnabled = false
            binding.btnSubmitComplaint.text = "Đang gửi khiếu nại..."

            lifecycleScope.launch {
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
