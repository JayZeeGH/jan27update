package com.example.myfslapplication

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Helper class to manage English/Filipino translations for FSL gestures.
 *
 * Key fix: speakWithTranslation() in GestureActivity passes a whole sentence
 * like "Hello Good morning". We now handle that by:
 *   1. Trying the full sentence as one key first (e.g. "good morning")
 *   2. Falling back to word-by-word translation
 *   3. Returning the original word if no translation exists
 */
class TranslationHelper(context: Context) {

    private val TAG = "TranslationHelper"

    private val translations: JSONObject = try {
        val jsonString = context.assets.open("translations.json")
            .bufferedReader().use { it.readText() }
        JSONObject(jsonString).getJSONObject("translations")
    } catch (e: Exception) {
        Log.e(TAG, "Error loading translations: ${e.message}", e)
        JSONObject()
    }

    /**
     * Get translation for a single gesture key (e.g. "hello", "good morning").
     * The key lookup is case-insensitive and trimmed.
     */
    fun getTranslation(gestureName: String, language: String): String {
        return try {
            val key = gestureName.lowercase().trim()
            if (!translations.has(key)) {
                Log.d(TAG, "No translation key for: '$key'")
                return gestureName
            }
            val obj = translations.getJSONObject(key)
            when (language.lowercase()) {
                "filipino", "fil" -> obj.optString("filipino", gestureName)
                else              -> obj.optString("english",  gestureName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTranslation error for '$gestureName': ${e.message}")
            gestureName
        }
    }

    /** True only when an exact key match exists. */
    fun hasTranslation(gestureName: String): Boolean =
        translations.has(gestureName.lowercase().trim())

    /** Returns both (english, filipino) for a single key. */
    fun getBothTranslations(gestureName: String): Pair<String, String> {
        return try {
            val key = gestureName.lowercase().trim()
            if (!translations.has(key)) return Pair(gestureName, gestureName)
            val obj = translations.getJSONObject(key)
            Pair(
                obj.optString("english",  gestureName),
                obj.optString("filipino", gestureName)
            )
        } catch (e: Exception) {
            Log.e(TAG, "getBothTranslations error: ${e.message}")
            Pair(gestureName, gestureName)
        }
    }

    /**
     * Translate a full sentence into the target language.
     *
     * Strategy (greedy longest-match):
     *   - Try 4-word phrase → 3-word → 2-word → 1-word at each position.
     *   - This handles keys like "good morning", "i love you", etc.
     *   - Words with no translation are kept as-is.
     *
     * Example:
     *   "Hello Good morning Thank you" → "Hello Magandang umaga Salamat"  (Filipino)
     */
    fun translateSentence(sentence: String, targetLanguage: String): String {
        val words  = sentence.trim().split("\\s+".toRegex())
        val result = StringBuilder()
        var i = 0

        while (i < words.size) {
            var matched = false

            // Try longest phrase first (up to 4 words), then shorter
            for (len in minOf(4, words.size - i) downTo 1) {
                val phrase = words.subList(i, i + len).joinToString(" ").lowercase()
                if (translations.has(phrase)) {
                    val obj = translations.getJSONObject(phrase)
                    val translated = when (targetLanguage.lowercase()) {
                        "filipino", "fil" -> obj.optString("filipino", words[i])
                        else              -> obj.optString("english",  words[i])
                    }
                    if (result.isNotEmpty()) result.append(" ")
                    result.append(translated)
                    i += len
                    matched = true
                    break
                }
            }

            if (!matched) {
                // No translation found — keep original word
                if (result.isNotEmpty()) result.append(" ")
                result.append(words[i])
                i++
            }
        }

        return result.toString()
    }

    /** Get all registered gesture keys. */
    fun getAllGestureNames(): List<String> {
        val names = mutableListOf<String>()
        try {
            val keys = translations.keys()
            while (keys.hasNext()) names.add(keys.next())
        } catch (e: Exception) {
            Log.e(TAG, "getAllGestureNames error: ${e.message}")
        }
        return names
    }
}