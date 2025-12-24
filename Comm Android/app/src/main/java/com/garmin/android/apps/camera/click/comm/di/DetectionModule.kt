package com.garmin.android.apps.camera.click.comm.di

import com.garmin.android.apps.camera.click.comm.detection.ButtonDetectionStrategy
import com.garmin.android.apps.camera.click.comm.detection.strategies.PackageSpecificStrategy
import com.garmin.android.apps.camera.click.comm.detection.strategies.SmartHeuristicStrategy
import com.garmin.android.apps.camera.click.comm.detection.strategies.UserPreferredButtonStrategy
import com.google.gson.Gson
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
     * Provides Gson for JSON serialization/deserialization
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
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
