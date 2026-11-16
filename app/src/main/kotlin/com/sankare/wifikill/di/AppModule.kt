package com.sankare.wifikill.di

import android.content.Context
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.StringProvider
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.platform.AndroidStringProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDispatchersProvider(): DispatchersProvider = object : DispatchersProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Main
    }

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = object : TimeProvider {
        override fun nowMillis(): Long = System.currentTimeMillis()
    }

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = object : IdGenerator {
        override fun newId(): String = UUID.randomUUID().toString()
    }

    @Provides
    @Singleton
    fun provideStringProvider(@ApplicationContext context: Context): StringProvider =
        AndroidStringProvider(context)
}
