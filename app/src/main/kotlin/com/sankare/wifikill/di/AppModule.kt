package com.sankare.wifikill.di

import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}
