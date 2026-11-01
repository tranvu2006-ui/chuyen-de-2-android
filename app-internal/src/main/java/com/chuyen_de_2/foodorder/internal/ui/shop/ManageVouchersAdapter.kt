package com.chuyen_de_2.foodorder.internal.ui.shop

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Voucher
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.databinding.ItemVoucherManageBinding

/**
 * Adapter danh sách mã giảm giá do Quán ăn tạo
 */
class ManageVouchersAdapter(
    private val onEditClick: (Voucher) -> Unit,
    private val onDeleteClick: (Voucher) -> Unit
) : RecyclerView.Adapter<ManageVouchersAdapter.ViewHolder>() {

    private val items = mutableListOf<Voucher>()

    fun submitList(newItems: List<Voucher>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVoucherManageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemVoucherManageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(voucher: Voucher) {
            binding.tvVoucherCode.text = voucher.code
            binding.tvVoucherDiscount.text = "-${voucher.discountAmount.toVndCurrency()}"
            binding.tvVoucherMinOrder.text = if (voucher.minOrderAmount > 0) {
                "Đơn tối thiểu: ${voucher.minOrderAmount.toVndCurrency()}"
            } else {
                "Áp dụng cho mọi giá trị đơn hàng"
            }

            binding.btnEditVoucher.setOnClickListener {
                onEditClick(voucher)
            }

            binding.btnDeleteVoucher.setOnClickListener {
                onDeleteClick(voucher)
            }
        }
    }
}
