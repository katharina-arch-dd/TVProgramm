package kat.android.tvprogramm.data.api

import kat.android.tvprogramm.data.model.ApiShowDto
import kat.android.tvprogramm.data.model.ApiTvResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TvApiService {

    @GET("api/v1/tv/program")
    suspend fun getProgramListings(
        @Query("date") date: String? = null,
        @Query("channel") channelId: String? = null
    ): ApiTvResponse

    @GET("api/v1/tv/shows/{id}")
    suspend fun getShowDetails(
        @Path("id") showId: String
    ): ApiShowDto
}

object TvApiClient {
    private const val DEFAULT_BASE_URL = "https://raw.githubusercontent.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun createService(baseUrl: String = DEFAULT_BASE_URL): TvApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TvApiService::class.java)
    }
}
