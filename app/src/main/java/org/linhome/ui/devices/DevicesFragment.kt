/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linhome-android
 * (see https://www.linhome.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.linhome.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.linhome.GenericFragment
import org.linhome.LinhomeApplication
import org.linhome.databinding.FragmentDevicesBinding
import org.linhome.entities.Device
import org.linhome.store.DeviceStore
import org.linhome.utils.DialogUtil
import org.linphone.mediastream.Log

class DevicesFragment : GenericFragment() {

    private lateinit var devicesViewModel: DevicesViewModel
    private lateinit var binding: FragmentDevicesBinding
    private val childProtectionObserver = Observer<Boolean> {
        updateDeviceButtonsVisibility()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDevicesBinding.inflate(inflater, container, false)
        devicesViewModel = ViewModelProvider(this).get(DevicesViewModel::class.java)
        devicesViewModel.selectedDevice =
            MutableLiveData<Device?>() // Android bug - onCreateView called on navigateUp()
        binding.lifecycleOwner = this
        binding.model = devicesViewModel

        binding.newDevice.setOnClickListener {
            if (LinhomeApplication.childProtectionModeState.value != true) {
                binding.newDevice.visibility = View.INVISIBLE
                val actionDetail = DevicesFragmentDirections.deviceNew()
                mainactivity.navController.navigate(actionDetail)
            }
        }

        binding.newDeviceNoneConfigured?.setOnClickListener {
            if (LinhomeApplication.childProtectionModeState.value != true) {
                binding.newDeviceNoneConfigured?.visibility = View.INVISIBLE
                val actionDetail = DevicesFragmentDirections.deviceNew()
                mainactivity.navController.navigate(actionDetail)
            }
        }

        binding.deviceList.layoutManager =
            LinearLayoutManager(activity, RecyclerView.VERTICAL, false)

        binding.deviceList.adapter = DevicesAdapter(
            devicesViewModel.devices,
            binding.deviceList,
            devicesViewModel.selectedDevice,
            this
        )

        if (LinhomeApplication.instance.smartPhone()) {
            devicesViewModel.selectedDevice.observe(viewLifecycleOwner, Observer { device ->
                device?.also {
                    if (LinhomeApplication.childProtectionModeState.value != true) {
                        val actionDetail = DevicesFragmentDirections.deviceInfo(it)
                        mainactivity.navController.navigate(actionDetail)
                    }
                }
            })
        } else {
            devicesViewModel.selectedDevice.observe(viewLifecycleOwner, Observer { device ->
                device?.also {
                    val isChildProtectionMode = LinhomeApplication.childProtectionModeState.value == true
                    binding.fragmentDeviceInfo?.editDevice?.visibility = if (isChildProtectionMode) View.GONE else if (it.isRemotelyProvisionned) View.GONE else View.VISIBLE
                }
            })
        }

        if (LinhomeApplication.instance.tablet()) {
            binding.fragmentDeviceInfo?.editDevice?.setOnClickListener {
                if (LinhomeApplication.childProtectionModeState.value != true) {
                    val actionDetail = DevicesFragmentDirections.deviceEditTablet()
                    actionDetail.device = devicesViewModel.selectedDevice.value
                    mainactivity.navController.navigate(actionDetail)
                }
            }
        }

        devicesViewModel.devices.observe(viewLifecycleOwner, { devices ->
            Log.i("[DevicesFragment] $devices ")
            (binding.deviceList.adapter as RecyclerView.Adapter).notifyDataSetChanged()
            binding.swiperefresh?.isRefreshing = false
        })

        devicesViewModel.syncFailed.observe(viewLifecycleOwner, { syncFailed ->
            binding.swiperefresh?.isRefreshing = false
            if (syncFailed)
                DialogUtil.error("vcard_sync_failed")
        })

        binding.swiperefresh?.setOnRefreshListener {
            if (LinhomeApplication.coreContext.core.isNetworkReachable != true) {
                binding.swiperefresh?.isRefreshing = false
                DialogUtil.error("no_network")
            } else if (LinhomeApplication.coreContext.core.callsNb == 0) {
                DeviceStore.serverFriendList?.synchronizeFriendsFromServer()
            }
        }

        binding.newDevice.visibility = View.GONE
        binding.newDeviceNoneConfigured?.visibility = View.GONE
        LinhomeApplication.childProtectionModeReady.observe(viewLifecycleOwner, Observer { ready ->
            if (ready == true) {
                updateDeviceButtonsVisibility()
            }
        })
        LinhomeApplication.childProtectionModeState.observe(viewLifecycleOwner, Observer { enabled ->
            if (LinhomeApplication.childProtectionModeReady.value == true) {
                updateDeviceButtonsVisibility()
            }
        })

        return binding.root
    }

    private fun isChildProtectionModeActive(): Boolean {
        val persisted = LinhomeApplication.getPersistedChildProtectionMode(requireContext())
        return persisted || LinhomeApplication.childProtectionModeState.value == true || LinhomeApplication.corePreferences.childProtectionMode
    }

    private fun updateDeviceButtonsVisibility() {
        val isChildProtectionMode = isChildProtectionModeActive()
        if (isChildProtectionMode) {
            binding.newDevice.visibility = View.GONE
            binding.newDeviceNoneConfigured?.visibility = View.GONE
        } else if (LinhomeApplication.instance.tablet()) {
            if (devicesViewModel.devices.value!!.size == 0) {
                binding.newDevice.visibility = View.GONE
                binding.newDeviceNoneConfigured?.visibility = View.VISIBLE
            } else {
                binding.newDevice.visibility = View.VISIBLE
                binding.newDeviceNoneConfigured?.visibility = View.GONE
            }
        } else {
            binding.newDevice.visibility = View.VISIBLE
            binding.newDeviceNoneConfigured?.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        if (LinhomeApplication.childProtectionModeReady.value == true) {
            updateDeviceButtonsVisibility()
        }
    }

    override fun onResume() {
        super.onResume()
        if (LinhomeApplication.childProtectionModeReady.value == true) {
            updateDeviceButtonsVisibility()
        }
        binding.swiperefresh?.isEnabled = DeviceStore.serverFriendList != null
    }

}
