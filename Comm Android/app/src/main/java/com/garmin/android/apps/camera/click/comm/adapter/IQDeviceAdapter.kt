package com.garmin.android.apps.camera.click.comm.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQDevice.IQDeviceStatus

/**
 * Adapter class for displaying Garmin devices in a RecyclerView.
 * This adapter handles the display of device information and status updates.
 * @param onItemClickListener Callback function for handling device selection
 * @param onSetDefaultDeviceListener Callback function for setting a device as default
 * @param getPreferredDeviceId Function to get the currently preferred device ID
 */
class IQDeviceAdapter(
    private val onItemClickListener: (IQDevice) -> Unit,
    private val onSetDefaultDeviceListener: (IQDevice) -> Unit,
    private val getPreferredDeviceId: () -> String?
) : ListAdapter<IQDevice, IQDeviceViewHolder>(IQDeviceItemDiffCallback()) {

    /**
     * Creates a new ViewHolder for displaying device information.
     * @param parent The parent ViewGroup
     * @param viewType The type of view to create
     * @return A new IQDeviceViewHolder instance
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IQDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return IQDeviceViewHolder(view, onItemClickListener, onSetDefaultDeviceListener, getPreferredDeviceId)
    }

    /**
     * Binds device data to a ViewHolder at the specified position.
     * @param holder The ViewHolder to bind data to
     * @param position The position of the item in the data set
     */
    override fun onBindViewHolder(holder: IQDeviceViewHolder, position: Int) {
        holder.bindTo(getItem(position))
    }

    /**
     * Updates the status of a specific device in the adapter.
     * @param device The device to update
     * @param status The new status of the device
     */
    fun updateDeviceStatus(device: IQDevice, status: IQDeviceStatus?) {
        val index = currentList
            .indexOfFirst { it.deviceIdentifier == device.deviceIdentifier }
        currentList[index].status = status
        notifyItemChanged(index)
    }
}

/**
 * DiffUtil callback for comparing IQDevice items in the adapter.
 * This class is used to efficiently update the RecyclerView when the data set changes.
 */
private class IQDeviceItemDiffCallback : DiffUtil.ItemCallback<IQDevice>() {
    /**
     * Checks if two items represent the same device.
     * @param oldItem The old device
     * @param newItem The new device
     * @return true if the items represent the same device
     */
    override fun areItemsTheSame(oldItem: IQDevice, newItem: IQDevice): Boolean =
        oldItem.deviceIdentifier == newItem.deviceIdentifier

    /**
     * Checks if the contents of two devices are the same.
     * @param oldItem The old device
     * @param newItem The new device
     * @return true if the device contents are the same
     */
    override fun areContentsTheSame(oldItem: IQDevice, newItem: IQDevice): Boolean =
        oldItem.deviceIdentifier == newItem.deviceIdentifier
            && oldItem.friendlyName == newItem.friendlyName
            && oldItem.status == newItem.status
}

/**
 * ViewHolder class for displaying Garmin device information in a RecyclerView.
 * @param view The view to hold
 * @param onItemClickListener Callback function for handling device selection
 * @param onSetDefaultDeviceListener Callback function for setting a device as default
 * @param getPreferredDeviceId Function to get the currently preferred device ID
 */
class IQDeviceViewHolder(
    private val view: View,
    private val onItemClickListener: (IQDevice) -> Unit,
    private val onSetDefaultDeviceListener: (IQDevice) -> Unit,
    private val getPreferredDeviceId: () -> String?
) : RecyclerView.ViewHolder(view) {

    /**
     * Binds device data to the ViewHolder's views.
     * @param device The device to display
     */
    fun bindTo(device: IQDevice) {
        val deviceName = when (device.friendlyName) {
            null -> device.deviceIdentifier.toString()
            else -> device.friendlyName
        }

        view.findViewById<TextView>(android.R.id.text1).text = deviceName
        view.findViewById<TextView>(android.R.id.text2).text = device.status?.name

        // Update status indicator color
        val statusIndicator = view.findViewById<View>(R.id.status_indicator)
        val statusColor = when (device.status) {
            IQDeviceStatus.CONNECTED -> R.color.success
            IQDeviceStatus.NOT_CONNECTED -> R.color.error
            else -> R.color.warning
        }
        statusIndicator.setBackgroundColor(ContextCompat.getColor(view.context, statusColor))

        // Show/hide default device indicator
        val defaultIndicator = view.findViewById<ImageView>(R.id.default_indicator)
        val preferredDeviceId = getPreferredDeviceId()
        val isDefaultDevice = preferredDeviceId == device.deviceIdentifier.toString()
        defaultIndicator.visibility = if (isDefaultDevice) View.VISIBLE else View.GONE

        // Set click listener
        view.setOnClickListener {
            onItemClickListener(device)
        }

        // Set long-press listener for context menu
        view.setOnLongClickListener {
            showContextMenu(device)
            true
        }
    }

    /**
     * Shows a context menu for device actions.
     * @param device The device to show the context menu for
     */
    private fun showContextMenu(device: IQDevice) {
        val popupMenu = PopupMenu(view.context, view)
        popupMenu.menuInflater.inflate(R.menu.device_context_menu, popupMenu.menu)
        
        // Update menu item text based on current state
        val preferredDeviceId = getPreferredDeviceId()
        val isCurrentlyDefault = preferredDeviceId == device.deviceIdentifier.toString()
        
        if (isCurrentlyDefault) {
            popupMenu.menu.findItem(R.id.set_default_device)?.title = "Remove as default device"
        } else {
            popupMenu.menu.findItem(R.id.set_default_device)?.title = "Set as default device"
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.set_default_device -> {
                    onSetDefaultDeviceListener(device)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }
}