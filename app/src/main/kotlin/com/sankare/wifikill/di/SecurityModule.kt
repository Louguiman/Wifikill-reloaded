package com.sankare.wifikill.di

import android.content.Context
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.domain.repository.CredentialProvider
import com.sankare.wifikill.core.security.EncryptedCredentialProvider
import com.sankare.wifikill.core.security.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager = KeystoreManager()

    @Provides
    @Singleton
    fun provideCredentialProvider(
        @ApplicationContext context: Context,
        keystoreManager: KeystoreManager,
        dispatchers: DispatchersProvider,
    ): CredentialProvider = EncryptedCredentialProvider(context, keystoreManager, dispatchers)
}
