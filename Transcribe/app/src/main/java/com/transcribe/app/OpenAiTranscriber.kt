package com.transcribe.app

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends audio to OpenAI and returns text.
 * lang = ""  -> auto-detect language (transcriptions, gpt-4o-transcribe)
 * lang = "he"-> force Hebrew recognition (transcriptions, gpt-4o-transcribe, language=he)
 * lang = "en"-> force ENGLISH OUTPUT via the translations endpoint (whisper-1), which
 *               translates any spoken language into English.
 */
object OpenAiTranscriber {

    private const val TRANSCRIBE_URL = "https://api.openai.com/v1/audio/transcriptions"
    private const val TRANSLATE_URL = "https://api.openai.com/v1/audio/translations"
    private const val MODEL_TX = "gpt-4o-transcribe"
    private const val MODEL_TR = "whisper-1"
    const val MAX_BYTES = 25L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class Ok(val text: String) : Result()
        data class Err(val errorRes: Int) : Result()
    }

    fun transcribe(apiKey: String, audio: ByteArray, fileName: String, mime: String?, lang: String = ""): Result {
        if (audio.isEmpty()) return Result.Err(R.string.err_no_file)
        if (audio.size > MAX_BYTES) return Result.Err(R.string.err_too_big)

        val mediaType = (mime ?: "application/octet-stream").toMediaTypeOrNull()
        val translate = (lang == "en")
        val url = if (translate) TRANSLATE_URL else TRANSCRIBE_URL

        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, audio.toRequestBody(mediaType))
            .addFormDataPart("model", if (translate) MODEL_TR else MODEL_TX)
            .addFormDataPart("response_format", "text")
        if (!translate && lang.isNotEmpty()) {
            builder.addFormDataPart("language", lang)
        }
        if (!translate && (lang == "he" || lang.isEmpty())) {
            builder.addFormDataPart("prompt", "תמלול הקלטה קולית. שמור על פיסוק תקין.")
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(builder.build())
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                val payload = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> {
                        val text = payload.trim()
                        if (text.isEmpty()) Result.Err(R.string.empty_result) else Result.Ok(text)
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
