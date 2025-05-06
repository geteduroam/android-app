package app.eduroam.geteduroam.di.module

import android.content.Context
import app.eduroam.geteduroam.BuildConfig
import app.eduroam.geteduroam.di.api.GetEduroamApi
import app.eduroam.geteduroam.di.api.response.ApiResponseAdapterFactory
import app.eduroam.geteduroam.di.assist.AuthenticationAssistant
import app.eduroam.geteduroam.di.repository.NotificationRepository
import app.eduroam.geteduroam.di.repository.StorageRepository
import app.eduroam.geteduroam.models.Profile
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object EduroamModule {

    @Provides
    @Singleton
    internal fun provideApi(retrofit: Retrofit): GetEduroamApi =
        retrofit.create(GetEduroamApi::class.java)

    @Provides
    @Singleton
    internal fun providesStorageRepository(
        @ApplicationContext context: Context,
    ) = StorageRepository(context)

    @Provides
    @Singleton
    internal fun providesNotificationRepository(
        @ApplicationContext context: Context,
    ) = NotificationRepository(context)


    @Provides
    fun providesOkHttp(
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(loggingInterceptor)
        }
        builder.addInterceptor(object: Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request()
                @Suppress("KotlinConstantConditions")
                val appName = if (BuildConfig.FLAVOR_brand == "eduroam") {
                    "geteduroam-android"
                } else {
                    "getgovroam-android"
                }
                val device = android.os.Build.DEVICE
                val model = android.os.Build.MODEL
                val manufacturer = android.os.Build.MANUFACTURER
                val androidVersion = android.os.Build.VERSION.RELEASE
                val versionWithoutBuild = BuildConfig.VERSION_NAME.split("(").first()

                val newRequest = request.newBuilder()
                    .header("User-Agent", "$appName/${versionWithoutBuild} (${BuildConfig.VERSION_CODE}; Android ${androidVersion}; $manufacturer $device $model)")
                    .build()
                return chain.proceed(newRequest)
            }
        })
        return builder.build()
    }

    @Provides
    @Singleton
    internal fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    @Provides
    @Singleton
    internal fun providesAuthenticationAssist(
    ) = AuthenticationAssistant()

    @Provides
    @Singleton
    internal fun provideJson() = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    internal fun provideEduroamRetrofit(
        client: Lazy<OkHttpClient>,
        json: Json
    ): Retrofit {
        return Retrofit.Builder().callFactory { client.get().newCall(it) }
            .addCallAdapterFactory(ApiResponseAdapterFactory.create())
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .baseUrl(BuildConfig.DISCOVERY_BASE_URL).build()
    }
}