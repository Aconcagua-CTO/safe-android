package io.gnosis.safe.multichain.di

import dagger.Module
import dagger.Provides
import io.gnosis.data.repositories.ChainInfoRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.data.repositories.TokenRepository
import io.gnosis.safe.multichain.MultichainFeatureFlag
import io.gnosis.safe.multichain.navigation.MultichainNavigationHelper
import io.gnosis.safe.multichain.repositories.MultichainSafeRepository
import io.gnosis.safe.multichain.services.MultichainBalanceService
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.settings.app.SettingsHandler
import pm.gnosis.svalinn.common.PreferencesManager
import javax.inject.Singleton

/**
 * Dagger module for multichain dependencies
 * Provides all multichain-related services and repositories
 */
@Module
class MultichainModule {

    @Provides
    @Singleton
    fun provideMultichainSafeRepository(
        safeRepository: SafeRepository,
        chainInfoRepository: ChainInfoRepository,
        preferencesManager: PreferencesManager
    ): MultichainSafeRepository {
        return MultichainSafeRepository(
            safeRepository,
            chainInfoRepository,
            preferencesManager
        )
    }

    @Provides
    @Singleton
    fun provideMultichainBalanceService(
        tokenRepository: TokenRepository,
        appDispatchers: AppDispatchers
    ): MultichainBalanceService {
        return MultichainBalanceService(
            tokenRepository,
            appDispatchers
        )
    }

    @Provides
    @Singleton
    fun provideMultichainFeatureFlag(
        settingsHandler: SettingsHandler
    ): MultichainFeatureFlag {
        return MultichainFeatureFlag(settingsHandler)
    }

    @Provides
    @Singleton
    fun provideMultichainNavigationHelper(
        multichainFeatureFlag: MultichainFeatureFlag
    ): MultichainNavigationHelper {
        return MultichainNavigationHelper(multichainFeatureFlag)
    }

    @Provides
    @Singleton
    fun provideMultichainMigrationHelper(
        multichainFeatureFlag: MultichainFeatureFlag,
        safeRepository: SafeRepository,
        multichainSafeRepository: MultichainSafeRepository
    ): io.gnosis.safe.multichain.migration.MultichainMigrationHelper {
        return io.gnosis.safe.multichain.migration.MultichainMigrationHelper(
            multichainFeatureFlag,
            safeRepository,
            multichainSafeRepository
        )
    }

    @Provides
    @Singleton
    fun provideMultichainErrorHandler(
        multichainFeatureFlag: MultichainFeatureFlag
    ): io.gnosis.safe.multichain.error.MultichainErrorHandler {
        return io.gnosis.safe.multichain.error.MultichainErrorHandler(multichainFeatureFlag)
    }

    @Provides
    @Singleton
    fun provideMultichainPerformanceMonitor(): io.gnosis.safe.multichain.performance.MultichainPerformanceMonitor {
        return io.gnosis.safe.multichain.performance.MultichainPerformanceMonitor()
    }
}
