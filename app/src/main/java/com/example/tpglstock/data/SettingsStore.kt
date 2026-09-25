package com.example.tpglstock.data

import android.content.Context
import com.example.tpglstock.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiSettings(
    /** Optional in-app override. Empty means use the key built in from secrets.properties. */
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val customInstructions: String = DEFAULT_INSTRUCTIONS,
) {
    val effectiveApiKey: String get() = apiKey.ifBlank { BuildConfig.DEEPSEEK_API_KEY }
    val isConfigured: Boolean get() = effectiveApiKey.isNotBlank()
    val usesBuiltInKey: Boolean get() = apiKey.isBlank() && BuildConfig.DEEPSEEK_API_KEY.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "deepseek-flash"
        val DEFAULT_INSTRUCTIONS = """
            Short names our supervisor uses:
            - "Red cap", "White cap", "Blue cap", "Yellow cap", "Lemon green cap" = BOTTLE COVERS in that colour.
            - "Plugs" = DROPPER PLUGS.
            - "Star bottle" = POWER ZONE (STAR BOTTLES). "Top Rank bottle" = POWER ZONE BOTTLES (TOP RANK).
            - "Covers" or "Jar cover" listed with jar containers = JAR COVER.
            - "Debbies cover" = DEBBIES COVER. "Debbies container" = DEBBIES CONTAINER (300ml Clear).
            - "Spray bottle 500ml White" = SPRAY BOTTLES, size 500ml, colour White.
            - "Jar container 400ml" = JAR CONTAINERS 400ml Clear (the only 400ml jar). "Jar container 250ml" with no colour: ask whether Clear or White.
            - "Paridox cover" is a new product in category PARIDOX COVER unless the catalog already has it.
            Other notes:
            - "Beach" is a colour name we use; don't correct it to "Bleach".
            - Blow material and injection material are raw materials counted in bags.
        """.trimIndent()

        /** Earlier default, replaced automatically if the owner never edited it. */
        val PREVIOUS_DEFAULT_INSTRUCTIONS = """
            - Our WhatsApp reports list stock under category headings, e.g. "SPRAY BOTTLES" then "(500ml) Clear = 8 bags (1,600 pcs)". Treat a dated full report as a stock count.
            - "Beach" is a colour name we use; don't correct it to "Bleach".
            - "Bottles cover red" means Bottle Covers, colour Red.
            - Blow material and injection material are raw materials counted in bags.
        """.trimIndent()
    }
}

/** Local settings. The API key stays on this device only. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tpgl_settings", Context.MODE_PRIVATE)

    private val _ai = MutableStateFlow(
        AiSettings(
            apiKey = prefs.getString(KEY_API, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, null)?.takeIf { it.startsWith("deepseek") } ?: AiSettings.DEFAULT_MODEL,
            customInstructions = prefs.getString(KEY_PROMPT, null)
                ?.takeUnless { it == AiSettings.PREVIOUS_DEFAULT_INSTRUCTIONS }
                ?: AiSettings.DEFAULT_INSTRUCTIONS,
        ),
    )
    val ai: StateFlow<AiSettings> = _ai.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString(KEY_NAME, null) ?: DEFAULT_NAME)
    /** What the app calls the person using this phone. Kumar until changed on the You tab. */
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_NAME, name.trim()).apply()
        _userName.value = name.trim()
    }

    fun save(settings: AiSettings) {
        prefs.edit()
            .putString(KEY_API, settings.apiKey.trim())
            .putString(KEY_MODEL, settings.model.trim().ifBlank { AiSettings.DEFAULT_MODEL })
            .putString(KEY_PROMPT, settings.customInstructions)
            .apply()
        _ai.value = settings.copy(
            apiKey = settings.apiKey.trim(),
            model = settings.model.trim().ifBlank { AiSettings.DEFAULT_MODEL },
        )
    }

    private companion object {
        const val KEY_API = "deepseek_api_key"
        const val KEY_MODEL = "deepseek_model"
        const val KEY_PROMPT = "ai_custom_instructions"
        const val KEY_NAME = "user_name"
        const val DEFAULT_NAME = "Kumar"
    }
}
