package app.eduroam.geteduroam.models

import android.os.Parcelable
import app.eduroam.geteduroam.extensions.removeNonSpacingMarks
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.util.Locale

@Parcelize
@Serializable
data class Organization(
    val country: String,
    val id: String,
    val name: Map<String, String>,
    val profiles: List<Profile>,
) : Parcelable {

    @IgnoredOnParcel
    var matchWordsLevel: Int = 0

    @IgnoredOnParcel
    var matchWords : List<String> = emptyList()

    fun getLocalizedName(): String {
        val userLanguage = Locale.getDefault().language.lowercase()
        return name[userLanguage] ?: // 1st option: the name in the user's language
            name[LANGUAGE_KEY_FALLBACK] ?: // 2nd option: the name in the fallback language (english)
            name.values.firstOrNull() ?: // 3rd option: any name we can find
            id // 4th option: the ID, which is always set
    }

    /**
     * Improves the word matching list.
     * Each time this is called, the list will be expanded with better matching results
     * If the results cannot be improved anymore, the function will return false
     */
    fun improveMatchWords() : Boolean {
        if (matchWordsLevel == 0) {
            matchWords = name.values.toList()
            matchWordsLevel = 1
            return true
        } else if (matchWordsLevel == 1) {
            // Split on anything which is non-alphanumeric
            val namesWords = name.values.map {  it.split(DELIMITER).filter { it.isNotEmpty() }}.toMutableList()
            val namesAbbreviations = namesWords.map { nameWords -> nameWords.map { word -> word.first() }.joinToString("") }
            val words = mutableListOf<String>()
            words += name.values.toList()
            words += namesWords.flatten()
            words += namesAbbreviations
            matchWords = words
            matchWordsLevel = 2
            return true
        } else if (matchWordsLevel == 2) {
            matchWords = matchWords.map { it.removeNonSpacingMarks() }
            matchWordsLevel = 3
        }
        return false
    }

    companion object {
        private val DELIMITER = "\\W+".toRegex()

        const val LANGUAGE_KEY_FALLBACK = "any"
    }
}