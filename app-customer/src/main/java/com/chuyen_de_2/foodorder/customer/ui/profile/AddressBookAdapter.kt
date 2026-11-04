package com.chuyen_de_2.foodorder.customer.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.CustomerAddress
import com.chuyen_de_2.foodorder.customer.databinding.ItemCustomerAddressBinding

class AddressBookAdapter(
    private val onSelectClick: (CustomerAddress) -> Unit,
    private val onSetDefaultClick: (CustomerAddress) -> Unit,
    private val onDeleteClick: (CustomerAddress) -> Unit
) : ListAdapter<CustomerAddress, AddressBookAdapter.AddressViewHolder>(DiffCallback) {

    inner class AddressViewHolder(private val binding: ItemCustomerAddressBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(address: CustomerAddress) {
            // Nhãn hiển thị kèm icon
            val labelIcon = when {
                address.label.contains("Công ty", ignoreCase = true) -> "🏢 "
                address.label.contains("Khác", ignoreCase = true) -> "📍 "
                else -> "🏠 "
            }
            binding.tvAddressLabel.text = if (address.label.startsWith("🏢") || address.label.startsWith("🏠") || address.label.startsWith("📍")) {
                address.label
            } else {
                "$labelIcon${address.label}"
            }

            // Badge mặc định
            binding.tvDefaultBadge.visibility = if (address.isDefault) View.VISIBLE else View.GONE
            binding.layoutDefaultAction.visibility = if (address.isDefault) View.GONE else View.VISIBLE

            // Thông tin người nhận
            val name = address.receiverName.ifBlank { "Người nhận" }
            val phone = address.receiverPhone.ifBlank { "Chưa có SĐT" }
            binding.tvReceiverInfo.text = "$name • $phone"

            // Địa chỉ đầy đủ
            binding.tvFullAddress.text = address.fullAddress()

            // Click toàn card
            binding.root.setOnClickListener {
                onSelectClick(address)
            }

            // Click đặt mặc định
            binding.btnSetDefault.setOnClickListener {
                onSetDefaultClick(address)
            }

            // Click xóa
            binding.btnDeleteAddress.setOnClickListener {
                onDeleteClick(address)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        val binding = ItemCustomerAddressBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AddressViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CustomerAddress>() {
        override fun areItemsTheSame(oldItem: CustomerAddress, newItem: CustomerAddress): Boolean {
            return oldItem.addressId == newItem.addressId
        }

        override fun areContentsTheSame(oldItem: CustomerAddress, newItem: CustomerAddress): Boolean {
            return oldItem == newItem
        }
    }
}
