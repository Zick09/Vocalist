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
    private val wordPairs = mutableListOf<WordPair>()
    private var translator: Translator? = null

    data class WordPair(
        val original: String,
        val translated: String,
        val delay: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupButtons()
        initializeTextToSpeech()
        updateRowCount()
        addInitialWordPairItem()
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
        if (wordPairs.isEmpty()) {
            Toast.makeText(this, "No word pairs to play", Toast.LENGTH_SHORT).show()
            return
        }

        isPlaying = true
        currentIndex = 0
        binding.buttonPlay.isEnabled = false
        binding.buttonStop.isEnabled = true

        playNextWord()
    }

    private fun playNextWord() {
        if (!isPlaying || currentIndex >= wordPairs.size) {
            stopPlayback()
            return
        }

        val wordPair = wordPairs[currentIndex]
        
        // Speak original word
        textToSpeech.speak(wordPair.original, TextToSpeech.QUEUE_FLUSH, null, null)
        
        // Schedule translated word
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isPlaying) {
                textToSpeech.speak(wordPair.translated, TextToSpeech.QUEUE_FLUSH, null, null)
                
                // Schedule next word
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    currentIndex++
                    playNextWord()
                }, (wordPair.delay * 1000).toLong())
            }
        }, 1000) // Wait 1 second before speaking translation
    }

    private fun stopPlayback() {
        isPlaying = false
        binding.buttonPlay.isEnabled = true
        binding.buttonStop.isEnabled = false
        textToSpeech.stop()
    }

    fun addWordPair(original: String, translated: String, delay: Int) {
        val wordPair = WordPair(original, translated, delay)
        wordPairs.add(wordPair)
        updateRowCount()
        addWordPairView(wordPair)
    }

    private fun addWordPairView(wordPair: WordPair) {
        val wordPairView = LayoutInflater.from(this).inflate(R.layout.word_pair_item, binding.containerWordPairs, false)
        
        val originalText = wordPairView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTextWord)
        val translatedText = wordPairView.findViewById<TextView>(R.id.textViewTranslation)
        val deleteButton = wordPairView.findViewById<ImageButton>(R.id.buttonDelete)
        val translateButton = wordPairView.findViewById<ImageButton>(R.id.buttonTranslate)
        val addButton = wordPairView.findViewById<Button>(R.id.buttonAdd)
        
        originalText.setText(wordPair.original)
        translatedText.text = wordPair.translated
        
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
        
        // Delete button handler
        deleteButton.setOnClickListener {
            val index = wordPairs.indexOf(wordPair)
            if (index != -1) {
                wordPairs.removeAt(index)
                binding.containerWordPairs.removeView(wordPairView)
                updateRowCount()
            }
        }
        
        // Add button handler
        addButton.setOnClickListener {
            val original = originalText.text.toString()
            val translated = translatedText.text.toString()
            if (original.isNotEmpty() && translated.isNotEmpty()) {
                val delayText = binding.spinnerDelay.selectedItem.toString()
                val delay = delayText.replace("s", "").toIntOrNull() ?: 2
                addWordPair(original, translated, delay)
            }
        }
        
        binding.containerWordPairs.addView(wordPairView)
    }

    private fun updateRowCount() {
        binding.textViewRowCount.text = "${wordPairs.size} rows"
    }

    private fun addInitialWordPairItem() {
        val wordPairView = LayoutInflater.from(this).inflate(R.layout.word_pair_item, binding.containerWordPairs, false)
        
        val originalText = wordPairView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTextWord)
        val translatedText = wordPairView.findViewById<TextView>(R.id.textViewTranslation)
        val translateButton = wordPairView.findViewById<ImageButton>(R.id.buttonTranslate)
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
        
        // Add button handler
        addButton.setOnClickListener {
            val original = originalText.text.toString()
            val translated = translatedText.text.toString()
            if (original.isNotEmpty() && translated.isNotEmpty()) {
                val delayText = binding.spinnerDelay.selectedItem.toString()
                val delay = delayText.replace("s", "").toIntOrNull() ?: 2
                addWordPair(original, translated, delay)
                
                // Clear the input fields
                originalText.text?.clear()
                translatedText.text = "Translation will appear here"
            }
        }
        
        binding.containerWordPairs.addView(wordPairView)
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
        textToSpeech.shutdown()
        translator?.close()
    }
}
