package com.chuyen_de_2.foodorder.internal.ui.ship

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.databinding.ItemShipperTripBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShipperIncomeAdapter : RecyclerView.Adapter<ShipperIncomeAdapter.TripViewHolder>() {

    private val trips = mutableListOf<Order>()

    fun submitList(list: List<Order>) {
        trips.clear()
        trips.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemShipperTripBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

    override fun getItemCount(): Int = trips.size

    inner class TripViewHolder(
        private val binding: ItemShipperTripBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            val shortCode = if (order.orderId.length > 5) order.orderId.takeLast(6).uppercase() else order.orderId
            binding.tvTripOrderCode.text = "#ORD_OISHI_$shortCode"

            val fee = if (order.shippingFee > 0) order.shippingFee else 25000L
            binding.tvTripEarning.text = "+${fee.toVndCurrency()}"

            val timeFormat = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault())
            val dateStr = if (order.timestamp > 0) timeFormat.format(Date(order.timestamp)) else "Hôm nay"
            binding.tvTripTime.text = "$dateStr (Giao đúng hẹn)"

            val storeName = order.shopName.ifBlank { "Quán ăn" }
            val dest = order.address.ifBlank { "Địa chỉ khách hàng" }
            binding.tvTripRoute.text = "$storeName ➔ $dest"

            val isCod = order.paymentMethod.equals("COD", ignoreCase = true)
            if (isCod) {
                val codAmount = if (order.totalPayment > 0) order.totalPayment else (order.total + fee)
                binding.tvTripPaymentStatus.text = "COD đã thu: ${codAmount.toVndCurrency()}"
            } else {
                binding.tvTripPaymentStatus.text = "Ví điện tử / Đã thanh toán"
            }
        }
    }
}
