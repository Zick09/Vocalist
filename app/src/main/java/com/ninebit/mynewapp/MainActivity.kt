package com.ninebit.mynewapp

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.ninebit.mynewapp.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var textToSpeech: TextToSpeech
    private var isPlaying = false
    private var currentIndex = 0
    private var translator: Translator? = null
    private var currentHighlightedView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupButtons()
        initializeTextToSpeech()
        loadSavedData()
        updateRowCount()
        addInitialWordPairItem()
        // Show add button on the first row
        showAddButtonOnLastRow()
    }

    private fun setupSpinners() {
        // Language options
        val languages = arrayOf("English", "Spanish", "French", "German", "Italian", "Portuguese", "Russian", "Japanese", "Korean", "Chinese")
        
        val fromAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        fromAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFromLanguage.adapter = fromAdapter

        val toAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        toAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerToLanguage.adapter = toAdapter

        // Delay options
        val delays = arrayOf("1s", "2s", "3s", "5s")
        val delayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, delays)
        delayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDelay.adapter = delayAdapter

        // Set default selections
        binding.spinnerFromLanguage.setSelection(0) // English
        binding.spinnerToLanguage.setSelection(1) // Spanish
        binding.spinnerDelay.setSelection(1) // 2s
        
        // Add listeners for automatic saving
        binding.spinnerFromLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        binding.spinnerToLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        binding.spinnerDelay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.buttonPlay.setOnClickListener {
            if (!isPlaying) {
                startPlayback()
            }
        }

        binding.buttonStop.setOnClickListener {
            stopPlayback()
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "TextToSpeech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPlayback() {
        val wordPairViews = getWordPairViews()
        if (wordPairViews.isEmpty()) {
            Toast.makeText(this, "No word pairs to play", Toast.LENGTH_SHORT).show()
            return
        }

        isPlaying = true
        currentIndex = 0
        binding.buttonPlay.isEnabled = false
        binding.buttonStop.isEnabled = true

        playNextWord(wordPairViews)
    }

    private fun playNextWord(wordPairViews: List<View>) {
        if (!isPlaying || currentIndex >= wordPairViews.size) {
            stopPlayback()
            return
        }

        val wordPairView = wordPairViews[currentIndex]
        val originalText = wordPairView.findViewById<TextInputEditText>(R.id.editTextWord)
        val translatedText = wordPairView.findViewById<TextView>(R.id.textViewTranslation)
        
        val original = originalText.text.toString()
        val translated = translatedText.text.toString()
        
        if (original.isNotEmpty() && translated.isNotEmpty() && translated != "Translation will appear here") {
            // Get current language selections
            val fromLanguage = binding.spinnerFromLanguage.selectedItem.toString()
            val toLanguage = binding.spinnerToLanguage.selectedItem.toString()
            
            // Highlight the current word pair
            highlightWordPair(wordPairView)
            
            // Speak original word in source language
            speakInLanguage(original, fromLanguage)
            
            // Schedule translated word in target language
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isPlaying) {
                    speakInLanguage(translated, toLanguage)
                    
                    // Schedule next word
                    val delayText = binding.spinnerDelay.selectedItem.toString()
                    val delay = delayText.replace("s", "").toIntOrNull() ?: 2
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // Remove highlight from current word pair
                        removeHighlight(wordPairView)
                        currentIndex++
                        playNextWord(wordPairViews)
                    }, (delay * 1000).toLong())
                }
            }, 1000) // Wait 1 second before speaking translation
        } else {
            // Skip empty pairs and go to next
            currentIndex++
            playNextWord(wordPairViews)
        }
    }

    private fun speakInLanguage(text: String, language: String) {
        val locale = getLocaleForLanguage(language)
        textToSpeech.setLanguage(locale)
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun getLocaleForLanguage(language: String): Locale {
        return when (language) {
            "English" -> Locale.US
            "Spanish" -> Locale("es")
            "French" -> Locale.FRENCH
            "German" -> Locale.GERMAN
            "Italian" -> Locale.ITALIAN
            "Portuguese" -> Locale("pt")
            "Russian" -> Locale("ru")
            "Japanese" -> Locale.JAPANESE
            "Korean" -> Locale.KOREAN
            "Chinese" -> Locale.CHINESE
            else -> Locale.US
        }
    }

    private fun stopPlayback() {
        isPlaying = false
        binding.buttonPlay.isEnabled = true
        binding.buttonStop.isEnabled = false
        textToSpeech.stop()
        // Remove any remaining highlights
        removeAllHighlights()
    }

    private fun getWordPairViews(): List<View> {
        val views = mutableListOf<View>()
        for (i in 0 until binding.containerWordPairs.childCount) {
            views.add(binding.containerWordPairs.getChildAt(i))
        }
        return views
    }

    private fun updateRowCount() {
        val count = binding.containerWordPairs.childCount
        binding.textViewRowCount.text = "$count rows"
    }

    private fun showAddButtonOnLastRow() {
        val totalRows = binding.containerWordPairs.childCount
        if (totalRows > 0) {
            // Hide add button on all rows
            for (i in 0 until totalRows) {
                val rowView = binding.containerWordPairs.getChildAt(i)
                val addButton = rowView.findViewById<Button>(R.id.buttonAdd)
                addButton.visibility = View.GONE
            }
            // Show add button only on the last row
            val lastRowView = binding.containerWordPairs.getChildAt(totalRows - 1)
            val lastAddButton = lastRowView.findViewById<Button>(R.id.buttonAdd)
            lastAddButton.visibility = View.VISIBLE
        }
    }

    private fun highlightWordPair(wordPairView: View) {
        // Remove previous highlight
        removeAllHighlights()
        // Set new highlight
        wordPairView.setBackgroundResource(R.drawable.word_pair_highlighted_background)
        currentHighlightedView = wordPairView
    }

    private fun removeHighlight(wordPairView: View) {
        wordPairView.setBackgroundResource(R.drawable.word_pair_background)
        if (currentHighlightedView == wordPairView) {
            currentHighlightedView = null
        }
    }

    private fun removeAllHighlights() {
        currentHighlightedView?.setBackgroundResource(R.drawable.word_pair_background)
        currentHighlightedView = null
    }

    private fun playSingleWordPair(wordPairView: View) {
        val originalText = wordPairView.findViewById<TextInputEditText>(R.id.editTextWord)
        val translatedText = wordPairView.findViewById<TextView>(R.id.textViewTranslation)
        
        val original = originalText.text.toString()
        val translated = translatedText.text.toString()
        
        if (original.isNotEmpty() && translated.isNotEmpty() && translated != "Translation will appear here") {
            // Get current language selections
            val fromLanguage = binding.spinnerFromLanguage.selectedItem.toString()
            val toLanguage = binding.spinnerToLanguage.selectedItem.toString()
            
            // Highlight the word pair
            highlightWordPair(wordPairView)
            
            // Speak original word in source language
            speakInLanguage(original, fromLanguage)
            
            // Schedule translated word in target language
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                speakInLanguage(translated, toLanguage)
                
                // Remove highlight after a delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    removeHighlight(wordPairView)
                }, 2000) // Keep highlight for 2 seconds after speaking
            }, 1000) // Wait 1 second before speaking translation
        }
    }



    private fun addInitialWordPairItem() {
        val wordPairView = LayoutInflater.from(this).inflate(R.layout.word_pair_item, binding.containerWordPairs, false)
        
        val originalText = wordPairView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTextWord)
        val translatedText = wordPairView.findViewById<TextView>(R.id.textViewTranslation)
        val translateButton = wordPairView.findViewById<ImageButton>(R.id.buttonTranslate)
        val deleteButton = wordPairView.findViewById<ImageButton>(R.id.buttonDelete)
        val addButton = wordPairView.findViewById<Button>(R.id.buttonAdd)
        
        // Translate button handler
        translateButton.setOnClickListener {
            val text = originalText.text.toString()
            if (text.isNotEmpty()) {
                val fromLang = binding.spinnerFromLanguage.selectedItem.toString()
                val toLang = binding.spinnerToLanguage.selectedItem.toString()
                translateWord(text, fromLang, toLang) { translatedResult ->
                    runOnUiThread {
                        translatedText.text = translatedResult
                    }
                }
            }
        }
        
        // Right word click handler - play single word pair
        translatedText.setOnClickListener {
            playSingleWordPair(wordPairView)
        }
        
        // Delete button handler - clears fields or removes row
        deleteButton.setOnClickListener {
            val totalRows = binding.containerWordPairs.childCount
            if (totalRows == 1) {
                // If only one row, just clear the fields
                originalText.setText("")
                translatedText.text = "Translation will appear here"
            } else {
                // If more than one row, remove the row
                binding.containerWordPairs.removeView(wordPairView)
                updateRowCount()
                // Show add button on the last remaining row
                showAddButtonOnLastRow()
            }
            saveData()
        }
        
        // Add button handler - adds new row and hides this button
        addButton.setOnClickListener {
            // Hide this add button
            addButton.visibility = View.GONE
            // Add a new empty input row
            addInitialWordPairItem()
            saveData()
        }
        

        

        
        binding.containerWordPairs.addView(wordPairView)
        updateRowCount()
        // Show add button on the newly added row (which is now the last row)
        showAddButtonOnLastRow()
    }

    fun translateWord(text: String, fromLanguage: String, toLanguage: String, callback: (String) -> Unit) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(getLanguageCode(fromLanguage))
            .setTargetLanguage(getLanguageCode(toLanguage))
            .build()
        
        translator = Translation.getClient(options)
        
        translator?.downloadModelIfNeeded()
            ?.addOnSuccessListener {
                translator?.translate(text)
                    ?.addOnSuccessListener { translatedText ->
                        callback(translatedText)
                    }
                    ?.addOnFailureListener { exception ->
                        callback("Translation failed: ${exception.message}")
                    }
            }
            ?.addOnFailureListener { exception ->
                callback("Model download failed: ${exception.message}")
            }
    }

    private fun getLanguageCode(language: String): String {
        return when (language) {
            "English" -> "en"
            "Spanish" -> "es"
            "French" -> "fr"
            "German" -> "de"
            "Italian" -> "it"
            "Portuguese" -> "pt"
            "Russian" -> "ru"
            "Japanese" -> "ja"
            "Korean" -> "ko"
            "Chinese" -> "zh"
            else -> "en"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveData()
        textToSpeech.shutdown()
        translator?.close()
    }

    private fun saveData() {
        val sharedPrefs = getSharedPreferences("WordPairsData", MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        // Save selected languages and delay
        editor.putInt("fromLanguageIndex", binding.spinnerFromLanguage.selectedItemPosition)
        editor.putInt("toLanguageIndex", binding.spinnerToLanguage.selectedItemPosition)
        editor.putInt("delayIndex", binding.spinnerDelay.selectedItemPosition)

        // Save word pairs
        val wordPairs = mutableListOf<String>()
        for (i in 0 until binding.containerWordPairs.childCount) {
            val rowView = binding.containerWordPairs.getChildAt(i)
            val originalText = rowView.findViewById<TextInputEditText>(R.id.editTextWord)
            val translatedText = rowView.findViewById<TextView>(R.id.textViewTranslation)
            
            val original = originalText.text.toString()
            val translated = translatedText.text.toString()
            
            if (original.isNotEmpty() && translated.isNotEmpty() && translated != "Translation will appear here") {
                wordPairs.add("$original|$translated")
            }
        }
        
        editor.putStringSet("wordPairs", wordPairs.toSet())
        editor.apply()
    }

    private fun loadSavedData() {
        val sharedPrefs = getSharedPreferences("WordPairsData", MODE_PRIVATE)
        
        // Load selected languages and delay
        val fromLanguageIndex = sharedPrefs.getInt("fromLanguageIndex", 0)
        val toLanguageIndex = sharedPrefs.getInt("toLanguageIndex", 1)
        val delayIndex = sharedPrefs.getInt("delayIndex", 1)
        
        binding.spinnerFromLanguage.setSelection(fromLanguageIndex)
        binding.spinnerToLanguage.setSelection(toLanguageIndex)
        binding.spinnerDelay.setSelection(delayIndex)
        
        // Load word pairs
        val wordPairsSet = sharedPrefs.getStringSet("wordPairs", setOf())
        if (wordPairsSet != null && wordPairsSet.isNotEmpty()) {
            // Clear the initial empty row
            binding.containerWordPairs.removeAllViews()
            
            // Add saved word pairs
            for (wordPair in wordPairsSet) {
                val parts = wordPair.split("|")
                if (parts.size == 2) {
                    addSavedWordPair(parts[0], parts[1])
                }
            }
        }
    }

    private fun addSavedWordPair(original: String, translated: String) {
        val wordPairView = LayoutInflater.from(this).inflate(R.layout.word_pair_item, binding.containerWordPairs, false)
        
        val originalText = wordPairView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTextWord)
        val translatedText = wordPairView.findViewById<TextView>(R.id.textViewTranslation)
        val translateButton = wordPairView.findViewById<ImageButton>(R.id.buttonTranslate)
        val deleteButton = wordPairView.findViewById<ImageButton>(R.id.buttonDelete)
        val addButton = wordPairView.findViewById<Button>(R.id.buttonAdd)
        
        // Set saved values
        originalText.setText(original)
        translatedText.text = translated
        
        // Translate button handler
        translateButton.setOnClickListener {
            val text = originalText.text.toString()
            if (text.isNotEmpty()) {
                val fromLang = binding.spinnerFromLanguage.selectedItem.toString()
                val toLang = binding.spinnerToLanguage.selectedItem.toString()
                translateWord(text, fromLang, toLang) { translatedResult ->
                    runOnUiThread {
                        translatedText.text = translatedResult
                    }
                }
            }
        }
        
        // Right word click handler - play single word pair
        translatedText.setOnClickListener {
            playSingleWordPair(wordPairView)
        }
        
        // Delete button handler - clears fields or removes row
        deleteButton.setOnClickListener {
            val totalRows = binding.containerWordPairs.childCount
            if (totalRows == 1) {
                // If only one row, just clear the fields
                originalText.setText("")
                translatedText.text = "Translation will appear here"
            } else {
                // If more than one row, remove the row
                binding.containerWordPairs.removeView(wordPairView)
                updateRowCount()
                // Show add button on the last remaining row
                showAddButtonOnLastRow()
            }
        }
        
        // Add button handler - adds new row and hides this button
        addButton.setOnClickListener {
            // Hide this add button
            addButton.visibility = View.GONE
            // Add a new empty input row
            addInitialWordPairItem()
        }
        
        binding.containerWordPairs.addView(wordPairView)
        updateRowCount()
        // Show add button on the newly added row (which is now the last row)
        showAddButtonOnLastRow()
    }
}
