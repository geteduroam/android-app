package app.eduroam.geteduroam.di.api

import app.eduroam.geteduroam.models.DiscoveryResult
import app.eduroam.geteduroam.models.LetswifiResult
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

interface GetEduroamApi {
    @GET("v3/discovery.json")
    suspend fun discover(): Response<DiscoveryResult>

    @GET
    @Headers("Accept: application/json")
    suspend fun getLetswifiConfig(
        @Url letswifiEndpoint: String
    ): Response<LetswifiResult>
}