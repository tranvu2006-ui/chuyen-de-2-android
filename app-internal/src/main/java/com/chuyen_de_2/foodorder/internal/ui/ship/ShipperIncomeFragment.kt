package com.chuyen_de_2.foodorder.internal.ui.ship

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.databinding.FragmentShipperIncomeBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tên chức năng: Quản Lý Thu Nhập & Ví Tiền Của Tài Xế (Shipper Income & Earnings)
 * Cách hoạt động:
 *  - Thu thập danh sách các cuốc giao hàng hoàn tất (COMPLETED) của tài xế.
 *  - Tính toán tổng số dư ví, thu nhập trong ngày và tuần.
 *  - Hiển thị lịch sử các chuyến xe đã hoàn thành kèm cước phí giao nhận thực nhận.
 *  - Cung cấp bộ lọc theo mốc thời gian: Hôm nay, Tuần này, Tháng này.
 * Luồng hướng tới:
 *  - Minh bạch tiền công giao hàng, phục vụ quyết toán thu nhập cho tài xế.
 */
@AndroidEntryPoint
class ShipperIncomeFragment : Fragment() {

    private var _binding: FragmentShipperIncomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShipperViewModel by activityViewModels()
    private val incomeAdapter = ShipperIncomeAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShipperIncomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Tên chức năng: Khởi tạo View màn hình thu nhập
     * Cách hoạt động: Thiết lập RecyclerView lịch sử chuyến, gắn tab bộ lọc thời gian và quan sát dữ liệu thu nhập.
     * Luồng hướng tới: Hiển thị bảng kê thu nhập.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilterTabs()
        observeData()
    }

    /**
     * Tên chức năng: Cấu hình danh sách chuyến đi đã hoàn thành
     * Cách hoạt động: Gắn [ShipperIncomeAdapter] với LayoutManager tuyến tính.
     * Luồng hướng tới: Render từng dòng cuốc xe đã giao thành công.
     */
    private fun setupRecyclerView() {
        binding.rvCompletedTrips.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = incomeAdapter
        }
    }

    /**
     * Tên chức năng: Bộ lọc thời gian thu nhập
     * Cách hoạt động: Chuyển đổi giữa Hôm nay, Tuần này, Tháng này để lọc danh sách các chuyến tương ứng.
     * Luồng hướng tới: Gọi [filterTrips].
     */
    private var currentFilterPeriod = "TODAY"

    private fun setupFilterTabs() {
        binding.toggleGroupTimeFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentFilterPeriod = when (checkedId) {
                    binding.btnFilterToday.id -> "TODAY"
                    binding.btnFilterWeek.id -> "WEEK"
                    binding.btnFilterMonth.id -> "MONTH"
                    else -> "TODAY"
                }
                filterTrips(currentFilterPeriod)
            }
        }
    }

    private fun observeData() {
        viewModel.assignedOrders.observe(viewLifecycleOwner) {
            filterTrips(currentFilterPeriod)
        }
    }

    private fun filterTrips(period: String) {
        val allCompleted = viewModel.assignedOrders.value?.filter { it.status == Order.Status.COMPLETED } ?: emptyList()

        val startOfToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfWeek = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayEarn = allCompleted.filter {
            val t = if (it.createdAt > 0) it.createdAt else it.timestamp
            t >= startOfToday || t == 0L
        }.sumOf { it.shippingFee }

        val weekEarn = allCompleted.filter {
            val t = if (it.createdAt > 0) it.createdAt else it.timestamp
            t >= startOfWeek || t == 0L
        }.sumOf { it.shippingFee }

        val totalEarn = allCompleted.sumOf { it.shippingFee }

        val filteredList = when (period) {
            "TODAY" -> allCompleted.filter {
                val t = if (it.createdAt > 0) it.createdAt else it.timestamp
                t >= startOfToday || t == 0L
            }
            "WEEK" -> allCompleted.filter {
                val t = if (it.createdAt > 0) it.createdAt else it.timestamp
                t >= startOfWeek || t == 0L
            }
            "MONTH" -> allCompleted.filter {
                val t = if (it.createdAt > 0) it.createdAt else it.timestamp
                t >= startOfMonth || t == 0L
            }
            else -> allCompleted
        }

        binding.tvTotalBalance.text = "+${totalEarn.toVndCurrency()}"
        binding.tvTodayIncome.text = "+${todayEarn.toVndCurrency()}"
        binding.tvWeekIncome.text = "+${weekEarn.toVndCurrency()}"

        if (filteredList.isNotEmpty()) {
            binding.tvEmptyIncome.visibility = View.GONE
            binding.rvCompletedTrips.visibility = View.VISIBLE
            incomeAdapter.submitList(filteredList)
            binding.tvCompletedTripsCount.text = "${filteredList.size} cuốc"
        } else {
            binding.tvEmptyIncome.visibility = View.VISIBLE
            binding.rvCompletedTrips.visibility = View.GONE
            incomeAdapter.submitList(emptyList())
            binding.tvCompletedTripsCount.text = "0 cuốc"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
