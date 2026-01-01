package ie.app.minimap.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface QrApiService {

    @Streaming
    @GET
    suspend fun fetchLongText(
        @Url fullUrl: String
    ): ResponseBody
}
