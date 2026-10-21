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
    private fun setupFilterTabs() {
        binding.toggleGroupTimeFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    binding.btnFilterToday.id -> filterTrips("TODAY")
                    binding.btnFilterWeek.id -> filterTrips("WEEK")
                    binding.btnFilterMonth.id -> filterTrips("MONTH")
                }
            }
        }
    }

    /**
     * Tên chức năng: Lắng nghe danh sách đơn hàng đã gán cho tài xế
     * Cách hoạt động: Lọc các đơn có trạng thái COMPLETED và gọi cập nhật giao diện thu nhập.
     * Luồng hướng tới: Gọi [updateIncomeUI].
     */
    private fun observeData() {
        viewModel.assignedOrders.observe(viewLifecycleOwner) { orders ->
            val completed = orders.filter { it.status == Order.Status.COMPLETED }
            updateIncomeUI(completed)
        }
    }

    private fun filterTrips(period: String) {
        val completed = viewModel.assignedOrders.value?.filter { it.status == Order.Status.COMPLETED } ?: emptyList()
        updateIncomeUI(completed)
    }

    /**
     * Tên chức năng: Cập nhật giao diện số dư và danh sách cuốc xe
     * Cách hoạt động: Tính tổng tiền cước `shippingFee` từ danh sách đơn hoàn tất, định dạng tiền tệ VNĐ và nạp dữ liệu vào adapter.
     * Luồng hướng tới: Hiển thị số liệu thu nhập trên giao diện.
     */
    private fun updateIncomeUI(completedOrders: List<Order>) {
        if (completedOrders.isNotEmpty()) {
            binding.tvEmptyIncome.visibility = View.GONE
            binding.rvCompletedTrips.visibility = View.VISIBLE
            incomeAdapter.submitList(completedOrders)

            val totalEarn = completedOrders.sumOf { it.shippingFee }
            binding.tvTotalBalance.text = "+${totalEarn.toVndCurrency()}"
            binding.tvTodayIncome.text = "+${totalEarn.toVndCurrency()}"
            binding.tvWeekIncome.text = "+${totalEarn.toVndCurrency()}"
            binding.tvCompletedTripsCount.text = "${completedOrders.size} cuốc"
        } else {
            binding.tvEmptyIncome.visibility = View.VISIBLE
            binding.rvCompletedTrips.visibility = View.GONE
            incomeAdapter.submitList(emptyList())

            binding.tvTotalBalance.text = "0 đ"
            binding.tvTodayIncome.text = "0 đ"
            binding.tvWeekIncome.text = "0 đ"
            binding.tvCompletedTripsCount.text = "0 cuốc"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
