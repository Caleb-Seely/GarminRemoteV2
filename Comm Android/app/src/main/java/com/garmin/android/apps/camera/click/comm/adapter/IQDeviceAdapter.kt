package com.garmin.android.apps.camera.click.comm.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
 */
class IQDeviceAdapter(
    private val onItemClickListener: (IQDevice) -> Unit
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
        return IQDeviceViewHolder(view, onItemClickListener)
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
 */
class IQDeviceViewHolder(
    private val view: View,
    private val onItemClickListener: (IQDevice) -> Unit
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

        view.setOnClickListener {
            onItemClickListener(device)
        }
    }
}