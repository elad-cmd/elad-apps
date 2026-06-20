package com.transcribe.app

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends an audio file to OpenAI's transcription endpoint and returns Hebrew text.
 * Uses the gpt-4o-transcribe model, which handles Hebrew (and mixed Hebrew/English/
 * Arabic) well. The request goes straight from the device to OpenAI — no middle server.
 */
object OpenAiTranscriber {

    private const val ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
    private const val MODEL = "gpt-4o-transcribe"
    const val MAX_BYTES = 25L * 1024 * 1024 // OpenAI hard limit

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class Ok(val text: String) : Result()
        /** errorRes is a string resource id from strings.xml */
        data class Err(val errorRes: Int) : Result()
    }

    fun transcribe(apiKey: String, audio: ByteArray, fileName: String, mime: String?, lang: String = ""): Result {
        if (audio.isEmpty()) return Result.Err(R.string.err_no_file)
        if (audio.size > MAX_BYTES) return Result.Err(R.string.err_too_big)

        val mediaType = (mime ?: "application/octet-stream").toMediaTypeOrNull()
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, audio.toRequestBody(mediaType))
            .addFormDataPart("model", MODEL)
            .addFormDataPart("response_format", "text")
        if (lang.isNotEmpty()) {
            builder.addFormDataPart("language", lang)
        }
        if (lang == "he" || lang.isEmpty()) {
            builder.addFormDataPart("prompt", "תמלול הקלטה קולית. שמור על פיסוק תקין.")
        }
        val body = builder.build()

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                val payload = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> {
                        val text = payload.trim()
                        if (text.isEmpty()) Result.Err(R.string.empty_result)
                        else Result.Ok(text)
                    }
                    resp.code == 401 || resp.code == 403 -> Result.Err(R.string.err_auth)
                    resp.code == 413 -> Result.Err(R.string.err_too_big)
                    else -> Result.Err(R.string.err_generic)
                }
            }
        } catch (e: IOException) {
            Result.Err(R.string.err_network)
        } catch (e: Exception) {
            Result.Err(R.string.err_generic)
        }
    }
}
