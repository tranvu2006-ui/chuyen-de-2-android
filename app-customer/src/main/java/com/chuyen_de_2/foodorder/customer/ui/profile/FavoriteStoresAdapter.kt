package com.chuyen_de_2.foodorder.customer.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.customer.databinding.ItemFavoriteStoreBinding
import com.chuyen_de_2.foodorder.customer.util.FavoriteStore

/**
 * Adapter hiển thị danh sách các quán ăn yêu thích của khách hàng
 */
class FavoriteStoresAdapter(
    private val onOrderClick: (FavoriteStore) -> Unit,
    private val onRemoveClick: (FavoriteStore) -> Unit
) : RecyclerView.Adapter<FavoriteStoresAdapter.ViewHolder>() {

    private val items = mutableListOf<FavoriteStore>()

    fun submitList(newItems: List<FavoriteStore>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteStoreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemFavoriteStoreBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(store: FavoriteStore) {
            binding.tvFavStoreName.text = store.storeName
            binding.tvFavStoreAddress.text = if (store.storeAddress.isNotBlank()) {
                store.storeAddress
            } else {
                "Quán ăn tại TP.HCM"
            }

            binding.btnOrderNow.setOnClickListener {
                onOrderClick(store)
            }

            binding.btnRemoveFav.setOnClickListener {
                onRemoveClick(store)
            }
        }
    }
}
