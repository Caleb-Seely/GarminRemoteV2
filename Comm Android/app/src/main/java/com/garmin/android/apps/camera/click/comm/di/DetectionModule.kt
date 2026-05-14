package com.garmin.android.apps.camera.click.comm.di

import android.graphics.Rect
import com.garmin.android.apps.camera.click.comm.detection.ButtonDetectionStrategy
import com.garmin.android.apps.camera.click.comm.detection.strategies.PackageSpecificStrategy
import com.garmin.android.apps.camera.click.comm.detection.strategies.SmartHeuristicStrategy
import com.garmin.android.apps.camera.click.comm.detection.strategies.UserPreferredButtonStrategy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Hilt module that provides dependencies for button detection.
 *
 * This module configures:
 * - The list of detection strategies in priority order
 * - Gson for JSON serialization
 * - All detection-related singletons
 */
@Module
@InstallIn(SingletonComponent::class)
object DetectionModule {

    /**
     * Provides the list of detection strategies.
     * Order doesn't matter here - AdaptiveButtonDetector will sort by priority.
     */
    @Provides
    @Singleton
    fun provideDetectionStrategies(
        userPreferredStrategy: UserPreferredButtonStrategy,
        packageSpecificStrategy: PackageSpecificStrategy,
        smartHeuristicStrategy: SmartHeuristicStrategy
    ): List<ButtonDetectionStrategy> {
        return listOf(
            userPreferredStrategy,
            packageSpecificStrategy,
            smartHeuristicStrategy
        )
    }

    /**
     * Provides Gson for JSON serialization/deserialization.
     * Registers a custom TypeAdapter for android.graphics.Rect because Gson's default
     * reflection-based adapter can fail on Android SDK classes that use Unsafe allocation.
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(Rect::class.java, object : TypeAdapter<Rect>() {
                override fun write(out: JsonWriter, value: Rect?) {
                    if (value == null) { out.nullValue(); return }
                    out.beginObject()
                    out.name("left").value(value.left)
                    out.name("top").value(value.top)
                    out.name("right").value(value.right)
                    out.name("bottom").value(value.bottom)
                    out.endObject()
                }
                override fun read(`in`: JsonReader): Rect {
                    if (`in`.peek() == JsonToken.NULL) { `in`.nextNull(); return Rect() }
                    var left = 0; var top = 0; var right = 0; var bottom = 0
                    `in`.beginObject()
                    while (`in`.hasNext()) {
                        when (`in`.nextName()) {
                            "left"   -> left   = `in`.nextInt()
                            "top"    -> top    = `in`.nextInt()
                            "right"  -> right  = `in`.nextInt()
                            "bottom" -> bottom = `in`.nextInt()
                            else     -> `in`.skipValue()
                        }
                    }
                    `in`.endObject()
                    return Rect(left, top, right, bottom)
                }
            })
            .create()
    }

    /**
     * Provides a CoroutineScope for dependency injection.
     * Uses SupervisorJob so individual coroutine failures don't cancel the entire scope.
     * Uses Dispatchers.Main.immediate for immediate dispatch when already on main thread.
     */
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
