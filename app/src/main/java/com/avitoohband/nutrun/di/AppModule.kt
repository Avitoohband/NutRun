package com.avitoohband.nutrun.di

import android.content.Context
import com.avitoohband.nutrun.data.NutRunDao
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.BackendFoodSearchService
import com.avitoohband.nutrun.data.FoodSearchService
import com.avitoohband.nutrun.auth.AuthenticationGateway
import com.avitoohband.nutrun.auth.FirebaseAuthenticationGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NutRunDatabase =
        NutRunDatabase.getInstance(context)

    @Provides
    fun dao(database: NutRunDatabase): NutRunDao = database.dao()

    @Provides
    @Singleton
    fun foodSearch(service: BackendFoodSearchService): FoodSearchService = service

    @Provides
    @Singleton
    fun authentication(gateway: FirebaseAuthenticationGateway): AuthenticationGateway = gateway
}
