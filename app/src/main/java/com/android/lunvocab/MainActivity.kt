package com.android.lunvocab

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var wordText: TextView
    private lateinit var extraText: TextView
    private lateinit var option1: MaterialButton
    private lateinit var option2: MaterialButton
    private lateinit var option3: MaterialButton
    private lateinit var option4: MaterialButton
    private var correctOption = ""
    private lateinit var rootButton: MaterialButton
    private lateinit var synonymButton: MaterialButton
    private lateinit var antonymButton: MaterialButton
    private lateinit var meaningButton: MaterialButton
    private lateinit var associationButton: MaterialButton
    private lateinit var filePickerButton: FloatingActionButton

    private lateinit var correctSound: MediaPlayer
    private lateinit var incorrectSound: MediaPlayer
    private lateinit var vibrator: Vibrator

    private var currentRowIndex = 1 // To ignore the Header
    private val rows = mutableListOf<List<String>>()  // To store rows of the CSV
    private val columnNames = mapOf(
        0 to "Word",
        1 to "Root",
        2 to "Synonym",
        3 to "Antonym",
        4 to "Meaning",
        5 to "Association"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wordText = findViewById(R.id.wordText)
        extraText = findViewById(R.id.extraText)
        option1 = findViewById(R.id.one)
        option2 = findViewById(R.id.two)
        option3 = findViewById(R.id.three)
        option4 = findViewById(R.id.four)

        setSynonymOptions()

        rootButton = findViewById(R.id.root)
        synonymButton = findViewById(R.id.synonym)
        antonymButton = findViewById(R.id.antonym)
        meaningButton = findViewById(R.id.meaning)
        associationButton = findViewById(R.id.association)
        filePickerButton = findViewById(R.id.filePicker)

        option1.setOnClickListener { checkAnswer(option1.text.toString()) }
        option2.setOnClickListener { checkAnswer(option2.text.toString()) }
        option3.setOnClickListener { checkAnswer(option3.text.toString()) }
        option4.setOnClickListener { checkAnswer(option4.text.toString()) }

        rootButton.setOnClickListener { showRootColumn() }
        synonymButton.setOnClickListener { showSynonymColumn() }
        antonymButton.setOnClickListener { showAntonymColumn() }
        meaningButton.setOnClickListener { showMeaningColumn() }
        associationButton.setOnClickListener { showAssociationColumn() }
        filePickerButton.setOnClickListener { openFilePicker() }

        // Initialize audio players for correct and incorrect sounds
        correctSound = MediaPlayer.create(this, R.raw.correct)
        incorrectSound = MediaPlayer.create(this, R.raw.incorrect)

        // Initialize vibrator
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                readCsvFromUri(uri)
            } else {
                Toast.makeText(this, "Failed to pick file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readCsvFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))

            rows.clear()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val columns = parseCsvLine(line)
                if (columns != null) {
                    rows.add(columns)
                }
            }
            reader.close()

            if (rows.isNotEmpty()) {
                wordText.text = "${rows[currentRowIndex][0]} (Row $currentRowIndex)"
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error reading file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseCsvLine(line: String?): List<String>? {
        line ?: return null
        val result = mutableListOf<String>()
        var currentValue = StringBuilder()
        var insideQuote = false

        for (char in line) {
            when (char) {
                '"' -> insideQuote = !insideQuote
                ',' -> {
                    if (insideQuote) {
                        currentValue.append(char)
                    } else {
                        result.add(currentValue.toString().trim())
                        currentValue = StringBuilder()
                    }
                }
                else -> currentValue.append(char)
            }
        }
        result.add(currentValue.toString().trim())
        return result
    }

    private fun setSynonymOptions() {
        if (rows.size > 1) {  // Ensure there's more than just the header
            // Skip the first row (header) and select a random row
            val randomRowIndex = (1 until rows.size).random()
            val currentWord = rows[randomRowIndex]
            wordText.text = "Row $randomRowIndex: ${currentWord[0]}"  // Display the word with row number for debugging

            // Ensure the correct synonym is non-empty
            val correctSynonym = currentWord.getOrNull(2)?.takeIf { it.isNotBlank() }
                ?: return showNextWord()  // If synonym is blank, skip to the next word
            correctOption = correctSynonym

            // Display row number for debugging in Toast
            Toast.makeText(this, "Word: ${currentWord[0]} (Row $randomRowIndex), Correct Synonym: $correctSynonym", Toast.LENGTH_LONG).show()

            // Filter rows that contain valid synonyms (non-empty in column 2)
            val validRows = rows.filter { it.size > 2 && it[2].isNotBlank() }

            // Ensure at least three incorrect options can be selected
            if (validRows.size < 4) {
                Toast.makeText(this, "Not enough data for options", Toast.LENGTH_SHORT).show()
                return
            }

            // Pick three random incorrect synonyms from other rows
            val incorrectOptions = validRows.filter { it != currentWord }
                .shuffled()
                .take(3)
                .mapIndexed { index, row -> "Row ${rows.indexOf(row)}: ${row[2]}" } // Show row number with options

            // Combine correct and incorrect options
            val allOptions = (incorrectOptions + "Row $randomRowIndex: $correctSynonym").shuffled()

            // Set the shuffled options to the buttons, with row number for debugging
            option1.text = allOptions[0]
            option2.text = allOptions[1]
            option3.text = allOptions[2]
            option4.text = allOptions[3]
        }
    }

    // Method to check the answer
    private fun checkAnswer(selectedOption: String) {
        if (selectedOption.contains(correctOption)) {
            // Play the correct sound
            playSound(R.raw.correct)

            // Fade out the current question and options
            fadeOutView(findViewById(R.id.wordText), 500)
            fadeOutView(findViewById(R.id.optionsContainer), 500)

            // Move to the next word after 1 second delay (this includes transition time)
            Handler(Looper.getMainLooper()).postDelayed({
                // Show the next word after the fade-out transition
                showNextWord()

                // Fade in the new question and options
                fadeInView(findViewById(R.id.wordText), 500)
                fadeInView(findViewById(R.id.optionsContainer), 500)

            }, 1000) // 1-second delay before moving to the next word
        } else {
            // Play the incorrect sound and vibrate
            playSound(R.raw.incorrect)

            // Vibrate the device for the incorrect option
            if (vibrator.hasVibrator()) {
                val vibrationEffect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            }
        }
    }

    // Fade out animation for views (e.g., question and options)
    private fun fadeOutView(view: View, duration: Long) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // Fade in animation for views (e.g., question and options)
    private fun fadeInView(view: View, duration: Long) {
        view.alpha = 0f // Start from invisible
        view.animate()
            .alpha(1f)  // Fade in to fully visible
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // Function to play the sound immediately, stopping any current playback
    private fun playSound(soundResId: Int) {
        // Create a new MediaPlayer instance every time to ensure it starts immediately
        val sound = MediaPlayer.create(this, soundResId)

        // Stop any currently playing sound, if needed
        sound.setOnPreparedListener {
            it.start() // Start the sound immediately
        }

        // Ensure the sound gets released after playback
        sound.setOnCompletionListener { mediaPlayer ->
            mediaPlayer.release()
        }

        // In case of an error, release the MediaPlayer
        sound.setOnErrorListener { mediaPlayer, _, _ ->
            mediaPlayer.release()
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Correct and incorrect sounds don't need to be released here anymore since each is released after use
    }


    private fun showNextWord() {
        if (currentRowIndex < rows.size - 1) {
            currentRowIndex++
            setSynonymOptions()  // Load the next word and set options
        } else {
            Toast.makeText(this, "End of list!", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showRootColumn() {
        showColumnInExtraText(1)
    }

    private fun showSynonymColumn() {
        showColumnInExtraText(2)
    }

    private fun showAntonymColumn() {
        showColumnInExtraText(3)
    }

    private fun showMeaningColumn() {
        showColumnInExtraText(4)
    }

    private fun showAssociationColumn() {
        showColumnInExtraText(5)
    }

    private fun showColumnInExtraText(columnIndex: Int) {
        if (rows.isNotEmpty() && columnIndex < rows[currentRowIndex].size) {
            val columnValue = rows[currentRowIndex][columnIndex]
            val columnName = columnNames[columnIndex] ?: "Column ${columnIndex + 1}"
            extraText.text = "$columnName: $columnValue"
        } else {
            extraText.text = "No data for ${columnNames[columnIndex] ?: "Column ${columnIndex + 1}"}"
        }
    }
}
 