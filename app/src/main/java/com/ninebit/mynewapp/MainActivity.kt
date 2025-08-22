package com.ninebit.mynewapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
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

        // Clear old format data to ensure proper order preservation
        val sharedPrefs = getSharedPreferences("WordPairsData", MODE_PRIVATE)
        if (sharedPrefs.contains("wordPairs") && !sharedPrefs.contains("wordPairsJson")) {
            val editor = sharedPrefs.edit()
            editor.remove("wordPairs")
            editor.apply()
            android.util.Log.d("MainActivity", "Cleared old format data")
        }

        setupSpinners()
        setupButtons()
        initializeTextToSpeech()
        loadSavedData()
        updateRowCount()
        
        // Only add initial empty row if no data was loaded
        if (binding.containerWordPairs.childCount == 0) {
            addInitialWordPairItem()
        }
        
        // Show add button on the last row
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

        // Delay options - from 1 to 4 seconds with 0.5 step
        val delays = arrayOf("1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0")
        val delayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, delays)
        delayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDelay.adapter = delayAdapter

        // Set default selections
        binding.spinnerFromLanguage.setSelection(0) // English
        binding.spinnerToLanguage.setSelection(1) // Spanish
        binding.spinnerDelay.setSelection(2) // 2.0s
        
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

    @SuppressLint("ResourceAsColor")
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
        
        // Change Stop button text and icon color to white, background to red
        binding.buttonStop.setTextColor(android.graphics.Color.WHITE)
        binding.buttonStop.iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        binding.buttonStop.setBackgroundColor(android.graphics.Color.RED)


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
            
            // Scroll to the current word pair to make it visible
            scrollToWordPair(wordPairView)
            
            // Speak original word in source language
            speakInLanguage(original, fromLanguage)
            
            // Schedule translated word in target language
            val delayText = binding.spinnerDelay.selectedItem.toString()
            val delay = delayText.toDoubleOrNull() ?: 2.0
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isPlaying) {
                    speakInLanguage(translated, toLanguage)
                    
                    // Schedule next word pair after the same delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // Remove highlight from current word pair
                        removeHighlight(wordPairView)
                        currentIndex++
                        playNextWord(wordPairViews)
                    }, (delay * 1000).toLong())
                }
            }, (delay * 1000).toLong()) // Use delay for both between words and between rows
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

    @SuppressLint("ResourceAsColor")
    private fun stopPlayback() {
        isPlaying = false
        binding.buttonPlay.isEnabled = true
        binding.buttonStop.isEnabled = false
        
        // Reset Stop button text and icon color to default, remove red background
        binding.buttonStop.setTextColor(R.color.inactive2)
        binding.buttonStop.iconTint = android.content.res.ColorStateList.valueOf(R.color.inactive2)
        binding.buttonStop.setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
        binding.textViewRowCount.text = "Total: $count"
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

    private fun scrollToWordPair(wordPairView: View) {
        // Find the ScrollView parent
        var parent = wordPairView.parent
        while (parent != null && parent !is android.widget.ScrollView) {
            parent = parent.parent
        }
        
        if (parent is android.widget.ScrollView) {
            // Calculate the position to scroll to
            val scrollView = parent
            val scrollViewTop = scrollView.top
            val wordPairTop = wordPairView.top
            val wordPairHeight = wordPairView.height
            
            // Scroll to make the word pair visible with some padding
            val scrollToY = wordPairTop - scrollViewTop - 100 // 100dp padding from top
            
            // Use smooth scroll for better UX
            scrollView.smoothScrollTo(0, scrollToY)
        }
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
            
            // Scroll to the word pair to make it visible
            scrollToWordPair(wordPairView)
            
            // Speak original word in source language
            speakInLanguage(original, fromLanguage)
            
            // Schedule translated word in target language
            val delayText = binding.spinnerDelay.selectedItem.toString()
            val delay = delayText.toDoubleOrNull() ?: 2.0
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                speakInLanguage(translated, toLanguage)
                
                // Remove highlight after the same delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    removeHighlight(wordPairView)
                }, (delay * 1000).toLong()) // Keep highlight for delay seconds after speaking
            }, (delay * 1000).toLong()) // Use delay between words
        }
    }



    private fun addInitialWordPairItem() {
        val wordPairView = LayoutInflater.from(this).inflate(R.layout.word_pair_item, binding.containerWordPairs, false)
        
        val originalText = wordPairView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTextWord)
        val translatedText = wordPairView.findViewById<TextView>(R.id.textViewTranslation)
        val translateButton = wordPairView.findViewById<ImageButton>(R.id.buttonTranslate)
        val deleteButton = wordPairView.findViewById<ImageButton>(R.id.buttonDelete)
        val addButton = wordPairView.findViewById<Button>(R.id.buttonAdd)
        
        // Add TextWatcher for automatic saving
        originalText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveData()
            }
        })
        
        // Translate button handler
        translateButton.setOnClickListener {
            val text = originalText.text.toString()
            if (text.isNotEmpty()) {
                val fromLang = binding.spinnerFromLanguage.selectedItem.toString()
                val toLang = binding.spinnerToLanguage.selectedItem.toString()
                translateWord(text, fromLang, toLang) { translatedResult ->
                    runOnUiThread {
                        translatedText.text = translatedResult
                        saveData() // Save after translation
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
                animateRowRemoval(wordPairView) {
                    updateRowCount()
                    // Show add button on the last remaining row
                    showAddButtonOnLastRow()
                }
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
        
        
        
        
        
        // Use animation for adding the row
        animateRowAddition(wordPairView)
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
            
            // Save only rows with content
            if (original.isNotEmpty()) {
                // If there's original text, save it with translation (even if empty)
                val translationToSave = if (translated.isNotEmpty() && translated != "Translation will appear here") {
                    translated
                } else {
                    ""
                }
                wordPairs.add("$original|$translationToSave")
            }
            // Skip completely empty rows - they will be added automatically when needed
        }
        
        // Save as JSON array to preserve order
        val jsonArray = org.json.JSONArray()
        for (wordPair in wordPairs) {
            jsonArray.put(wordPair)
        }
        val jsonString = jsonArray.toString()
        editor.putString("wordPairsJson", jsonString)
        editor.apply()
        
        // Debug log to verify order
        android.util.Log.d("MainActivity", "Saved word pairs in order: $jsonString")
    }

    private fun loadSavedData() {
        val sharedPrefs = getSharedPreferences("WordPairsData", MODE_PRIVATE)
        
        // Load selected languages and delay
        val fromLanguageIndex = sharedPrefs.getInt("fromLanguageIndex", 0)
        val toLanguageIndex = sharedPrefs.getInt("toLanguageIndex", 1)
        val delayIndex = sharedPrefs.getInt("delayIndex", 2) // Default to 2.0s
        
        binding.spinnerFromLanguage.setSelection(fromLanguageIndex)
        binding.spinnerToLanguage.setSelection(toLanguageIndex)
        binding.spinnerDelay.setSelection(delayIndex)
        
        // Load word pairs
        val wordPairsJson = sharedPrefs.getString("wordPairsJson", null)
        if (wordPairsJson != null && wordPairsJson.isNotEmpty()) {
            try {
                val jsonArray = org.json.JSONArray(wordPairsJson)
                // Clear the initial empty row
                binding.containerWordPairs.removeAllViews()
                
                // Debug log to verify loading order
                android.util.Log.d("MainActivity", "Loading word pairs in order: $wordPairsJson")
                
                // Add saved word pairs in order
                for (i in 0 until jsonArray.length()) {
                    val wordPair = jsonArray.getString(i)
                    val parts = wordPair.split("|")
                    if (parts.size >= 2) {
                        val original = parts[0]
                        val translated = parts[1]
                        
                        if (original.isEmpty() && translated.isEmpty()) {
                            // Skip completely empty rows - they will be added automatically if needed
                            continue
                        } else {
                            // Row with content
                            addSavedWordPair(original, translated)
                        }
                    }
                }
            } catch (e: Exception) {
                // Log error and start fresh if JSON parsing fails
                android.util.Log.e("MainActivity", "Error loading saved data: ${e.message}")
                // Clear any old format data to prevent future issues
                val editor = sharedPrefs.edit()
                editor.remove("wordPairs")
                editor.apply()
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
        
        // Add TextWatcher for automatic saving
        originalText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveData()
            }
        })
        
        // Set saved values
        originalText.setText(original)
        translatedText.text = if (translated.isEmpty()) {
            "Translation will appear here"
        } else {
            translated
        }
        
        // Translate button handler
        translateButton.setOnClickListener {
            val text = originalText.text.toString()
            if (text.isNotEmpty()) {
                val fromLang = binding.spinnerFromLanguage.selectedItem.toString()
                val toLang = binding.spinnerToLanguage.selectedItem.toString()
                translateWord(text, fromLang, toLang) { translatedResult ->
                    runOnUiThread {
                        translatedText.text = translatedResult
                        saveData() // Save after translation
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
                animateRowRemoval(wordPairView) {
                    updateRowCount()
                    // Show add button on the last remaining row
                    showAddButtonOnLastRow()
                }
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
        
        // Use animation for adding the row
        animateRowAddition(wordPairView)
        updateRowCount()
        // Show add button on the newly added row (which is now the last row)
        showAddButtonOnLastRow()
    }

    private fun animateRowRemoval(wordPairView: View, onComplete: () -> Unit) {
        // Create slide out animation to the right
        val slideOut = android.view.animation.AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
        slideOut.duration = 300 // 300ms duration
        
        slideOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                // Remove the view after animation completes
                binding.containerWordPairs.removeView(wordPairView)
                onComplete()
            }
        })
        
        wordPairView.startAnimation(slideOut)
    }

    private fun animateRowAddition(wordPairView: View) {
        // Set initial position (off-screen to the left)
        wordPairView.translationX = -wordPairView.width.toFloat()
        wordPairView.alpha = 0f
        
        // Add view to container
        binding.containerWordPairs.addView(wordPairView)
        
        // Animate in from left
        wordPairView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(300) // 300ms duration
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }
}
