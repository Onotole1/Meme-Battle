package ru.memebattle

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.memebattle.core.api.AuthApi
import ru.memebattle.core.api.RatingApi
import ru.memebattle.core.utils.getString

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@App)

            modules(
                listOf(
                    sharedPreferencesModule,
                    networkModule
                )
            )
        }
    }
}

val networkModule = module {
    single {
        OkHttpClient.Builder()
            .followSslRedirects(true)
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .addInterceptor(
                object : Interceptor {
                    override fun intercept(chain: Interceptor.Chain): Response {
                        val original = chain.request()
                        val token = get<SharedPreferences>().getString(PREFS_TOKEN)
                        return if (token != null) {
                            val requestBuilder = original.newBuilder()
                                .addHeader("Authorization", "Bearer $token")
                            val request = requestBuilder.build()
                            chain.proceed(request)
                        } else {
                            chain.proceed(original)
                        }
                    }
                }
            )
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl("https://memebattle.herokuapp.com/api/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(get<OkHttpClient>())
            .build()
    }

    single { get<Retrofit>().create(AuthApi::class.java) }
    single { get<Retrofit>().create(RatingApi::class.java) }
}

val sharedPreferencesModule = module {
    single { androidContext().getSharedPreferences("settings", Context.MODE_PRIVATE) }
}

const val PREFS_TOKEN = "token"
const val PREFS_EMAIL = "email"