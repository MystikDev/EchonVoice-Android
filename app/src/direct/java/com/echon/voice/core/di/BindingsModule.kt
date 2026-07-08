package com.echon.voice.core.di

import com.echon.voice.core.update.GithubReleaseManifestSource
import com.echon.voice.core.update.ReleaseManifestSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    @Singleton
    abstract fun bindReleaseManifestSource(impl: GithubReleaseManifestSource): ReleaseManifestSource
}
