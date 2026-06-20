package com.transcribe.app

import android.content.Context

/** Stores the user's OpenAI API key and preferred transcription language on the device. */
object Prefs {
    private const val FILE = "transcribe_prefs"
    private const val KEY_API = "api_key"
    private const val KEY_LANG = "lang" // "" = auto, "he", "en"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getKey(ctx: Context): String = sp(ctx).getString(KEY_API, "").orEmpty().trim()
    fun setKey(ctx: Context, value: String) { sp(ctx).edit().putString(KEY_API, value.trim()).apply() }
    fun hasKey(ctx: Context): Boolean = getKey(ctx).isNotEmpty()

    fun getLang(ctx: Context): String = sp(ctx).getString(KEY_LANG, "").orEmpty()
    fun setLang(ctx: Context, value: String) { sp(ctx).edit().putString(KEY_LANG, value).apply() }
}
