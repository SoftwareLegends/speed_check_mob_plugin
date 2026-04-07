package com.speedtest.sdk.net

import com.speedtest.sdk.model.TestFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * API for fetching the list of available test files from the server.
 */
class FilesApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FilesResponse(val files: List<TestFile>)

    /**
     * Fetch the list of test files from the server.
     */
    fun getFiles(): List<TestFile> {
        val request = Request.Builder()
            .url("$baseUrl/api/files")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response from /api/files")
            val parsed = json.decodeFromString<FilesResponse>(body)
            return parsed.files
        }
    }

    /**
     * Get the size of a specific file by name.
     * Falls back to known sizes if the file list hasn't been fetched.
     */
    fun getFileSize(fileName: String, fileList: List<TestFile>): Long {
        return fileList.find { it.name == fileName }?.size
            ?: KNOWN_FILE_SIZES[fileName]
            ?: throw IllegalArgumentException("Unknown file: $fileName")
    }

    companion object {
        /** Fallback file sizes when the server list is unavailable. */
        val KNOWN_FILE_SIZES = mapOf(
            "test-1MB.bin" to 1_048_576L,
            "test-5MB.bin" to 5_242_880L,
            "test-10MB.bin" to 10_485_760L,
            "test-25MB.bin" to 26_214_400L,
            "test-50MB.bin" to 52_428_800L,
            "test-100MB.bin" to 104_857_600L,
            "test-200MB.bin" to 209_715_200L,
            "test-300MB.bin" to 314_572_800L,
            "test-500MB.bin" to 524_288_000L,
            "test-1024MB.bin" to 1_073_741_824L,
        )
    }
}
