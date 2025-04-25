/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.connectiq.sample.comm.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.connectiq.sample.comm.Message

/**
 * Adapter class for displaying messages in a RecyclerView.
 * This adapter handles the display of messages that can be sent to a Garmin device.
 * @param onItemClickListener Callback function for handling message selection
 */
class MessagesAdapter(
    private val onItemClickListener: (Any) -> Unit
) : ListAdapter<Message, MessageViewHolder>(MessageItemDiffCallback()) {

    /**
     * Creates a new ViewHolder for displaying message information.
     * @param parent The parent ViewGroup
     * @param viewType The type of view to create
     * @return A new MessageViewHolder instance
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return MessageViewHolder(view, onItemClickListener)
    }

    /**
     * Binds message data to a ViewHolder at the specified position.
     * @param holder The ViewHolder to bind data to
     * @param position The position of the item in the data set
     */
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bindTo(getItem(position))
    }
}

/**
 * DiffUtil callback for comparing Message items in the adapter.
 * This class is used to efficiently update the RecyclerView when the data set changes.
 */
private class MessageItemDiffCallback : DiffUtil.ItemCallback<Message>() {
    /**
     * Checks if two items represent the same message.
     * @param oldItem The old message
     * @param newItem The new message
     * @return true if the items represent the same message
     */
    override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean =
        oldItem == newItem

    /**
     * Checks if the contents of two messages are the same.
     * @param oldItem The old message
     * @param newItem The new message
     * @return true if the message contents are the same
     */
    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean =
        oldItem == newItem
}

/**
 * ViewHolder class for displaying messages in a RecyclerView.
 * @param view The view to hold
 * @param onItemClickListener Callback function for handling message selection
 */
class MessageViewHolder(
    private val view: View,
    private val onItemClickListener: (Any) -> Unit
) : RecyclerView.ViewHolder(view) {

    /**
     * Binds message data to the ViewHolder's views.
     * @param message The message to display
     */
    fun bindTo(message: Message) {
        view.findViewById<TextView>(android.R.id.text1).text = message.text
        view.setOnClickListener {
            onItemClickListener(message.payload)
        }
    }
}