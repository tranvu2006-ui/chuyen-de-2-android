package com.chuyen_de_2.foodorder.customer.ui.complaint

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivityCustomerComplaintTrackingBinding
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Màn hình Theo Dõi Khiếu Nại & Xử Lý dành cho Khách hàng
 * - Lắng nghe danh sách khiếu nại theo thời gian thực (Realtime)
 * - Thống kê số lượng hồ sơ (Tổng, Đang xử lý, Đã có phán quyết)
 * - Lọc linh hoạt theo trạng thái: Chờ thẩm định, Đã xử phạt, Bị phạt ngược
 * - Hiển thị minh bạch cảnh báo chế tài xử phạt ngược nếu báo cáo sai sự thật
 * - Cho phép chạm vào từng hồ sơ để xem chi tiết biên bản giải quyết và ảnh bằng chứng
 */
@AndroidEntryPoint
class CustomerComplaintTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerComplaintTrackingBinding

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private lateinit var adapter: CustomerComplaintAdapter
    private var allComplaints: List<Complaint> = emptyList()
    private var currentFilterMode: FilterMode = FilterMode.ALL

    enum class FilterMode {
        ALL, PENDING, RESOLVED, COUNTER_PENALIZED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerComplaintTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, isLightStatusBar = true)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.nestedScrollView)

        setupToolbar()
        setupRecyclerView()
        setupFilterChips()
        observeComplaints()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_customer_complaint_tracking)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_new_complaint) {
                openNewComplaint()
                true
            } else {
                false
            }
        }

        binding.btnCreateComplaint.setOnClickListener {
            openNewComplaint()
        }
    }

    private fun openNewComplaint() {
        startActivity(Intent(this, ComplaintActivity::class.java))
    }

    private fun setupRecyclerView() {
        adapter = CustomerComplaintAdapter { complaint ->
            CustomerComplaintDetailBottomSheet.newInstance(complaint)
                .show(supportFragmentManager, "CustomerComplaintDetailBottomSheet")
        }
        binding.rvCustomerComplaints.layoutManager = LinearLayoutManager(this)
        binding.rvCustomerComplaints.adapter = adapter
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilterMode = when {
                checkedIds.contains(R.id.chipPending) -> FilterMode.PENDING
                checkedIds.contains(R.id.chipResolved) -> FilterMode.RESOLVED
                checkedIds.contains(R.id.chipCounterPenalized) -> FilterMode.COUNTER_PENALIZED
                else -> FilterMode.ALL
            }
            applyFilter()
        }
    }

    private fun observeComplaints() {
        val currentUserId = firebaseAuth.currentUser?.uid.orEmpty()
        if (currentUserId.isBlank()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch {
            firebaseRepository.observeCustomerComplaints(currentUserId).collectLatest { list ->
                allComplaints = list
                updateStatistics(list)
                applyFilter()
            }
        }
    }

    private fun updateStatistics(list: List<Complaint>) {
        val total = list.size
        val pending = list.count { it.status.equals(Complaint.ComplaintStatus.PENDING, ignoreCase = true) && !it.isComplainantPenalized }
        val resolved = list.count { it.status.equals(Complaint.ComplaintStatus.RESOLVED, ignoreCase = true) }

        binding.tvTotalCount.text = total.toString()
        binding.tvPendingCount.text = pending.toString()
        binding.tvResolvedCount.text = resolved.toString()
    }

    private fun applyFilter() {
        val filtered = when (currentFilterMode) {
            FilterMode.ALL -> allComplaints
            FilterMode.PENDING -> allComplaints.filter {
                it.status.equals(Complaint.ComplaintStatus.PENDING, ignoreCase = true) && !it.isComplainantPenalized
            }
            FilterMode.RESOLVED -> allComplaints.filter {
                it.status.equals(Complaint.ComplaintStatus.RESOLVED, ignoreCase = true)
            }
            FilterMode.COUNTER_PENALIZED -> allComplaints.filter {
                it.isComplainantPenalized
            }
        }

        adapter.submitList(filtered)
        val isEmpty = filtered.isEmpty()
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvCustomerComplaints.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
}
