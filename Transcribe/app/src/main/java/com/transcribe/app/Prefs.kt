package com.transcribe.app

import android.content.Context

/** Stores the user's OpenAI API key locally on the device. */
object Prefs {
    private const val FILE = "transcribe_prefs"
    private const val KEY_API = "api_key"

    fun getKey(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_API, "")
            .orEmpty()
            .trim()

    fun setKey(ctx: Context, value: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API, value.trim())
            .apply()
    }

    fun hasKey(ctx: Context): Boolean = getKey(ctx).isNotEmpty()
}
