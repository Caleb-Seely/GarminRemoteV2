/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.connectiq.sample.comm

import android.content.Context

/**
 * Data class representing a message that can be sent to a Garmin device.
 * @property text The display text for the message
 * @property payload The actual data to be sent to the device
 */
data class Message(val text: String, val payload: Any)

/**
 * Factory class that provides predefined messages for testing device communication.
 * These messages demonstrate various data types and structures that can be sent to Garmin devices.
 */
object MessageFactory {

    /**
     * Returns a list of sample messages that can be sent to a Garmin device.
     * The messages include various data types and structures to test device communication capabilities.
     * @param context The context used to access string resources
     * @return A list of predefined messages with different payload types
     */
    @JvmStatic
    fun getMessages(context: Context) = listOf(
        Message(
            context.getString(R.string.hello_world),
            context.getString(R.string.hello_world_message)
        ),
        Message(
            context.getString(R.string.short_string),
            context.getString(R.string.short_string_message)
        ),
        Message(
            context.getString(R.string.medium_string),
            context.getString(R.string.medium_string_message)
        ),
        Message(
            context.getString(R.string.long_string),
            context.getString(R.string.long_string_message)
        ),
        Message(
            context.getString(R.string.absurd_string),
            context.getString(R.string.absurd_message)
        ),
        Message(
            context.getString(R.string.array),
            listOf(
                "An",
                "array",
                "of",
                "strings",
                "and",
                "one",
                "pi",
                java.lang.Double.valueOf(3.14159265359)
            )
        ),
        Message(
            context.getString(R.string.dictionary),
            mapOf(
                "key1" to "value1",
                "key2" to null,
                "key3" to 42,
                "key4" to 123.456,
                "key5" to 433434344323411L,
                "key6" to 16777217.432
            )
        ),
        Message(
            context.getString(R.string.complex),
            listOf(
                "A string",
                listOf("A", "nested", "array"),
                mapOf(
                    "key1" to "A nested dictionary",
                    "key2" to "three strings...",
                    "key3" to "and one array",
                    "key4" to arrayOf(
                        "This array has two strings",
                        "and a nested dictionary!",
                        mapOf(
                            "one" to 1,
                            "two" to 2,
                            "three" to 3,
                            "four" to 4,
                            "five" to 5,
                            1.61803 to "G.R."
                        )
                    )
                ),
                "And one last  null",
                null
            )
        )
    )
}