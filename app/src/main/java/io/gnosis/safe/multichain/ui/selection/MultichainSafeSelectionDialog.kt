package io.gnosis.safe.multichain.ui.selection

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.DialogMultichainSafeSelectionBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.base.SafeOverviewNavigationHandler
import io.gnosis.safe.ui.base.fragment.BaseBottomSheetDialogFragment
import javax.inject.Inject

/**
 * Dialog for selecting multichain Safes
 * Shows Safes grouped by address with deployed chains listed
 */
class MultichainSafeSelectionDialog : BaseBottomSheetDialogFragment<DialogMultichainSafeSelectionBinding>() {

    @Inject
    lateinit var viewModel: MultichainSafeSelectionViewModel

    @Inject
    lateinit var adapter: MultichainSafeSelectionAdapter

    var navHandler: SafeOverviewNavigationHandler? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        navHandler = context as SafeOverviewNavigationHandler
    }

    override fun onDetach() {
        super.onDetach()
        navHandler = null
    }

    override fun screenId() = ScreenId.MULTICHAIN_SAFE_SELECT

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun viewModelProvider() = this

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): DialogMultichainSafeSelectionBinding =
        DialogMultichainSafeSelectionBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onViewCreated() - multichain safe selection dialog created")
        }

        with(binding) {
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapter
        }

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is MultichainSafeSelectionState.SafeListState -> {
                    adapter.setItems(state.listItems, state.activeMultichainSafe)
                    state.viewAction?.let { action ->
                        when (action) {
                            is BaseStateViewModel.ViewAction.CloseScreen -> {
                                dismiss()
                            }
                            is BaseStateViewModel.ViewAction.NavigateTo -> {
                                findNavController().navigate(action.navDirections)
                            }
                        }
                    }
                }
                is MultichainSafeSelectionState.AddSafeState -> {
                    state.viewAction?.let { action ->
                        when (action) {
                            is BaseStateViewModel.ViewAction.NavigateTo -> {
                                findNavController().navigate(action.navDirections)
                            }
                        }
                    }
                }
            }
        })

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onViewCreated() - loading multichain safes")
        }
        viewModel.loadMultichainSafes()
    }
    
    companion object {
        private const val TAG = "MultichainSafeSelectionDialog"
    }
}
