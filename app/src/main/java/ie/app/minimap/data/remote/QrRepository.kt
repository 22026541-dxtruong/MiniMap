package ie.app.minimap.data.remote

class QrRepository(
    private val api: QrApiService
) {

    suspend fun readTextSafely(url: String): String {
        val body = api.fetchLongText(url)
        return body.string()
    }

}
