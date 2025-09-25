package io.gnosis.safe.multichain.ui.selection

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.databinding.ItemAddSafeBinding
import io.gnosis.safe.databinding.ItemMultichainSafeBinding
import io.gnosis.safe.multichain.models.MultichainSafe
import io.gnosis.safe.multichain.models.MultichainSafeSelectionViewData
import io.gnosis.safe.ui.base.adapter.UnsupportedViewType
import io.gnosis.safe.utils.abbreviateEthAddress
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddressString
import java.lang.ref.WeakReference

/**
 * Adapter for displaying multichain Safes in selection dialog
 * Shows Safes grouped by address with chain information
 */
class MultichainSafeSelectionAdapter(
    private val clickListener: WeakReference<OnMultichainSafeSelectionItemClickedListener>
) : RecyclerView.Adapter<BaseMultichainSafeSelectionViewHolder>() {

    private val items = mutableListOf<MultichainSafeSelectionViewData>()
    
    companion object {
        private const val TAG = "MultichainSafeSelectionAdapter"
    }
    var activeMultichainSafe: MultichainSafe? = null
        set(value) {
            field = value
            notifyAllChanged()
        }

    fun setItems(items: List<MultichainSafeSelectionViewData>, activeMultichainSafe: MultichainSafe?) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "setItems() - setting ${items.size} items, active safe: ${activeMultichainSafe?.localName ?: "none"}")
        }
        this.activeMultichainSafe = activeMultichainSafe
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: BaseMultichainSafeSelectionViewHolder, position: Int) {
        when (holder) {
            is AddSafeHeaderViewHolder -> holder.bind()
            is MultichainSafeItemViewHolder -> {
                val safeItem = items[position] as MultichainSafeSelectionViewData.MultichainSafeItem
                holder.bind(safeItem.multichainSafe, safeItem.multichainSafe.address == activeMultichainSafe?.address)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseMultichainSafeSelectionViewHolder {
        return when (MultichainSafeSelectionViewTypes.values()[viewType]) {
            MultichainSafeSelectionViewTypes.HEADER_ADD_SAFE -> AddSafeHeaderViewHolder(
                ItemAddSafeBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                clickListener
            )
            MultichainSafeSelectionViewTypes.MULTICHAIN_SAFE -> MultichainSafeItemViewHolder(
                ItemMultichainSafeBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                clickListener
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return when (item) {
            is MultichainSafeSelectionViewData.AddSafeHeader -> MultichainSafeSelectionViewTypes.HEADER_ADD_SAFE.ordinal
            is MultichainSafeSelectionViewData.MultichainSafeItem -> MultichainSafeSelectionViewTypes.MULTICHAIN_SAFE.ordinal
            else -> throw UnsupportedViewType(item.toString())
        }
    }

    override fun getItemCount() = items.size

    private fun notifyAllChanged() {
        notifyItemRangeChanged(0, items.size)
    }

    enum class MultichainSafeSelectionViewTypes {
        HEADER_ADD_SAFE,
        MULTICHAIN_SAFE
    }

    interface OnMultichainSafeSelectionItemClickedListener {
        fun onMultichainSafeClicked(multichainSafe: MultichainSafe)
        fun onAddSafeClicked()
    }
}

/**
 * Base view holder for multichain Safe selection items
 */
abstract class BaseMultichainSafeSelectionViewHolder(
    viewBinding: ViewBinding
) : RecyclerView.ViewHolder(viewBinding.root)

/**
 * View holder for "Add Safe" header item
 * Reuses existing AddSafeBinding
 */
class AddSafeHeaderViewHolder(
    private val binding: ItemAddSafeBinding,
    private val clickListener: WeakReference<MultichainSafeSelectionAdapter.OnMultichainSafeSelectionItemClickedListener>
) : BaseMultichainSafeSelectionViewHolder(binding) {

    fun bind() {
        binding.root.setOnClickListener {
            clickListener.get()?.onAddSafeClicked()
        }
    }
}

/**
 * View holder for multichain Safe items
 * Displays Safe name, address, and list of deployed chains
 */
class MultichainSafeItemViewHolder(
    private val binding: ItemMultichainSafeBinding,
    private val clickListener: WeakReference<MultichainSafeSelectionAdapter.OnMultichainSafeSelectionItemClickedListener>
) : BaseMultichainSafeSelectionViewHolder(binding) {

    fun bind(multichainSafe: MultichainSafe, selected: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "bind() - binding multichain safe: ${multichainSafe.localName}, selected: $selected")
            Log.d(TAG, "bind() - address: ${multichainSafe.address.asEthereumAddressString()}")
            Log.d(TAG, "bind() - chains: ${multichainSafe.chainNames}")
        }
        
        with(binding) {
            safeName.text = multichainSafe.localName
            safeAddress.text = multichainSafe.address.asEthereumAddressChecksumString().abbreviateEthAddress(null)
            
            // Show chain names as comma-separated list
            safeChains.text = multichainSafe.chainNames
            
            // Set the Safe address for the blockies image
            safeImage.setAddress(multichainSafe.address)
            
            // Show selection indicator
            safeSelection.visible(selected, View.INVISIBLE)
            
            root.setOnClickListener {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "bind() - multichain safe clicked: ${multichainSafe.localName}")
                }
                clickListener.get()?.onMultichainSafeClicked(multichainSafe)
            }
        }
    }
    
    companion object {
        private const val TAG = "MultichainSafeItemViewHolder"
    }
}
