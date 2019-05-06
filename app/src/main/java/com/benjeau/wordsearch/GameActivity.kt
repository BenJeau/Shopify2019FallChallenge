package com.benjeau.wordsearch

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.flexbox.FlexboxLayout
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.collections.ArrayList
import android.animation.Animator
import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.net.Uri
import android.os.SystemClock
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.core.content.ContextCompat
import java.util.*
import com.google.android.gms.common.images.ImageManager

class GameActivity : AppCompatActivity() {

    /**
     * The words in the word bank
     */
    private lateinit var wordBank: ArrayList<String>

    /**
     * The state of the words of the word bank, the value is true if they have been found, false
     * otherwise
     */
    private lateinit var wordBankFound: ArrayList<Boolean>

    /**
     * The ArrayList of letters that are displayed in the board game
     */
    private lateinit var wordSearchLetters: ArrayList<String>

    /**
     * The link between the words of the word bank and indexes of their letters in the board
     */
    private lateinit var wordBankSearch: MutableMap<String, ArrayList<Int>>

    /**
     * The state of the letters. The numbers represents
     *      0 -> untouched
     *      1 -> touched
     *      2 -> untouched and part of a word found
     *      3 -> touched and part of a word found
     */
    private lateinit var letterStates: ArrayList<Int>

    /**
     * References to the views in the layout
     */
    private lateinit var chronometer: Chronometer
    private lateinit var letters: FlexboxLayout
    private lateinit var wordBankLayout: FlexboxLayout
    private lateinit var score: TextView
    private lateinit var gameBoardContent: ConstraintLayout
    private lateinit var gameBoardFinished: ConstraintLayout
    private lateinit var gameInfo: CardView
    private lateinit var gameBoard: CardView

    /**
     * The font used for the TextViews
     */
    private var typeface: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        initialSetup()
        finishedGame()
    }

    /**
     * Performs the initial setup of the game
     */
    private fun initialSetup() {
        setupViews()
        setupGame()

        // Defines the typeface used for the TextViews programmatically inserted
        typeface = ResourcesCompat.getFont(this, R.font.actor)

        // Sets the profile picture and fetches a dummy profile picture if the user is not signed in
        val profileIcon: ImageView = findViewById(R.id.profileIcon)

        val uri = getSharedPrefString("profilePictureURI")
        if (uri == null) {
            Glide.with(this)
                .load("https://api.adorable.io/avatars/100/wordSearch")
                .apply(RequestOptions.circleCropTransform())
                .into(profileIcon)
        } else {
            val imageManager = ImageManager.create(this)
            imageManager.loadImage(profileIcon, Uri.parse(uri))
        }

        // Sets the profile name if signed in
        val firstName: TextView = findViewById(R.id.firstName)
        val lastName: TextView = findViewById(R.id.lastName)

        val profileName = getSharedPrefString("profileName")
        if (profileName != null) {
            val name = profileName.split(" ")
            firstName.text = name.subList(0, name.size - 1).joinToString (separator = " "){ it }
            lastName.text = name.last()
        }

        // Sets action for the home button
        val homeIcon: ImageButton = findViewById(R.id.homeIcon)
        homeIcon.setOnClickListener { finish() }

        // Sets action for the buttons when ending the game
        val playAgainButton: Button = findViewById(R.id.playAgainButton)
        val exitButton: Button = findViewById(R.id.exitButton)
        playAgainButton.setOnClickListener { playAgain() }
        exitButton.setOnClickListener { finish() }
    }

    /**
     * Create references to the views often used
     */
    private fun setupViews() {
        letters = findViewById(R.id.letters)
        wordBankLayout = findViewById(R.id.wordBank)
        chronometer = findViewById(R.id.time)
        score = findViewById(R.id.score)
        gameBoardContent = findViewById(R.id.gameBoardContent)
        gameBoardFinished = findViewById(R.id.gameBoardFinished)
        gameInfo = findViewById(R.id.gameInfo)
        gameBoard = findViewById(R.id.gameBoard)
    }

    /**
     * Sets up a new game
     */
    private fun setupGame() {
        // Initializes the arrays
        wordSearchLetters = arrayListOf()
        wordBankSearch = mutableMapOf()
        letterStates = arrayListOf()
        wordBankFound = arrayListOf()
        wordBank = arrayListOf()

        // Clears the game information and start the chronometer
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.start()
        score.text = "0"

        // Clears the views to add the new words and letters
        letters.removeAllViews()
        wordBankLayout.removeAllViews()

        // Generates the words and letters for the upcoming game
        generateWordBank()
        createLetters()

        // Populates the TextViews for the game
        populateWordBank(typeface)
        populateWordSearchBoard(typeface)
    }

    /**
     * Adds every mandatory words and two random optional words to the word bank
     */
    private fun generateWordBank() {
        wordBank.addAll(WORDS)

        val firstRandom  = (0 until OPTIONAL_WORDS.size).random()
        var secondRandom: Int
        do {
            secondRandom = (0 until OPTIONAL_WORDS.size).random()
        } while (firstRandom == secondRandom)

        wordBank.add(OPTIONAL_WORDS[firstRandom])
        wordBank.add(OPTIONAL_WORDS[secondRandom])

        wordBank.sortByDescending {it.length}
    }

    /**
     * Create the array of letters representing the game board
     */
    private fun createLetters() {
        // Initializes the array with empty strings and their state
        for (i in 0..99) {
            wordSearchLetters.add("")
            letterStates.add(0)
        }

        // Adds words in the list of letters
        wordBank.forEach {
            val lines = (0..9).toMutableList()
            var isHorizontal = (0..1).random() == 1
            var changedOrientation = false
            var interfere = false

            // Tries to find a place where the word will not interfere with other existing words
            do {

                // If it interferes, change the orientation
                if (interfere) {
                    if (changedOrientation) {
                        changedOrientation = false
                        interfere = false
                        isHorizontal = !isHorizontal
                    } else {
                        changedOrientation = true
                        isHorizontal = !isHorizontal
                    }
                }

                // Get a random line and offset from the start of the line
                val line = lines[(0 until lines.size).random()]
                val offset = (0..(10 - it.length)).random()

                // Checks if it will interfere
                for (i in 0 until it.length) {
                    val index = if (isHorizontal) {
                        line * 10 + offset + i
                    } else {
                        offset * 10 + line + i * 10
                    }

                    if (wordSearchLetters[index] != "" && wordSearchLetters[index] != it[i].toString().toUpperCase()) {
                        interfere = true
                        if (changedOrientation) {
                            lines.remove(line)
                            if (lines.size == 0) {
                                // Start over TODO
                            }
                        }
                        break
                    }
                }

                // Adds it to the array of letters if it doesn't interfere
                if (!interfere) {
                    wordBankSearch[it] = arrayListOf()
                    for (i in 0 until it.length) {
                        val index = if (isHorizontal) {
                            line * 10 + offset + i
                        } else {
                            offset * 10 + line + i * 10
                        }

                        wordBankSearch[it]?.add(index)
                        wordSearchLetters[index] = it[i].toString().toUpperCase()
                    }
                }
            } while (interfere)
        }

        // Adds random letters to the spaces that are empty
        for (i in 0..99) {
            if (wordSearchLetters[i] == "") {
                wordSearchLetters[i] = (ALPHABET[(0 until ALPHABET.length).random()].toString())
            }
        }

        // Shuffles the word bank to make it look different each time
        wordBank.shuffle()
    }

    /**
     * Populates the words in the layout of the word search bank
     */
    private fun populateWordBank(typeface: Typeface?) {
        val padding = dpToPx(7)
        val fontSize = dpToPx(8).toFloat()
        val textColor = ContextCompat.getColor(this, R.color.white)

        wordBank.forEach {
            val text = TextView(this)
            text.text = it
            wordBankFound.add(false)
            wordBankLayout.addView(text)
            text.setTextColor(textColor)
            text.setPadding(padding, padding, padding, padding)
            text.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            text.textSize = fontSize
            text.typeface = typeface
            text.gravity = Gravity.CENTER
        }
    }

    /**
     * Populates the letters in the layout of the word search board
     */
    private fun populateWordSearchBoard(typeface: Typeface?) {
        val fontSize = dpToPx(10).toFloat()
        val textColor = ContextCompat.getColor(this, R.color.colorDarkGray)

        wordSearchLetters.forEachIndexed { index, letter ->
            val text = TextView(this)
            letters.addView(text)
            text.text = letter
            text.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            text.textSize = fontSize
            text.typeface = typeface
            text.gravity = Gravity.CENTER
            text.setTextColor(textColor)
            text.setOnClickListener { letterOnClick(index, text) }
            (text.layoutParams as FlexboxLayout.LayoutParams).flexBasisPercent = 0.0875f
        }
    }

    /**
     * The onClick callback for the letters' TextView
     *
     * @param index The index of the letter in the array of letters
     * @param view The letter's TextView
     */
    private fun letterOnClick(index: Int, view: TextView) {
        // Decides if the state of the letter should change
        var changeState = true

        // Checks if the letter is selected
        if (letterStates.contains(1) || letterStates.contains(3)) {
            val numSelected = letterStates.count { it == 1 || it == 3 }

            // If there's one letter selected, restrict the user to select only letters besides it.
            // Else, let the player select letters only in the line that he started and let the
            // player only select letters before and after the selected letters in the line
            if (numSelected == 1) {
                val letterIndex = if (letterStates.indexOf(1) >= 0) letterStates.indexOf(1) else letterStates.indexOf(3)
                val isTop = index == letterIndex - 10
                val isBottom = index == letterIndex + 10
                val isLeft = index == letterIndex - 1
                val isRight = index == letterIndex + 1
                when (letterIndex) {
                    // Checks if clicked on the same letter
                    index -> changeState = true
                    // Checks the top left corner
                    0 -> changeState = isRight || isBottom
                    // Checks the top right corner
                    9 -> changeState = isLeft || isBottom
                    // Checks the bottom left corner
                    90 -> changeState = isRight || isTop
                    // Checks the bottom right corner
                    99 -> changeState = isLeft || isTop
                    // Checks the top
                    in 1..8 -> changeState = isRight || isLeft || isBottom
                    // Checks the bottom
                    in 91..98 -> changeState = isRight || isLeft || isTop
                    // Checks the left
                    in 10 until 99 step 10 -> changeState = isRight || isBottom || isTop
                    // Checks the right
                    in 9 until 99 step 10 -> changeState = isLeft || isBottom || isTop
                    else -> changeState = isLeft || isRight || isBottom || isTop
                }
            } else {
                val selectedIndexes = letterStates.withIndex().filter { it.value == 1 || it.value == 3 }. map { it.index }
                val isHorizontal = selectedIndexes[0] in selectedIndexes[1]-1..selectedIndexes[1]+1
                val lineNumber: Int = if (isHorizontal) Math.floor(selectedIndexes[0] / 10.toDouble()).toInt() else selectedIndexes[0] % 10
                val firstOffset: Int = if (!isHorizontal) Math.floor(selectedIndexes.first() / 10.toDouble()).toInt() else selectedIndexes.first() % 10
                val lastOffset: Int = if (!isHorizontal) Math.floor(selectedIndexes.last() / 10.toDouble()).toInt() else selectedIndexes.last() % 10
                val currentOffset: Int = if (!isHorizontal) Math.floor(index / 10.toDouble()).toInt() else index % 10

                // Restricts the user to only select letters from the same line
                val sameLine = (if (isHorizontal) Math.floor(index / 10.toDouble()).toInt() else index % 10) == lineNumber
                val before = currentOffset == firstOffset - 1
                val after = currentOffset == lastOffset +1
                changeState = sameLine && ( before|| after) || letterStates[index] == 1|| letterStates[index] == 3
            }
        }

        // Updates the view of the letter according its previous state and updates the state
        if (changeState) {
            when {
                letterStates[index] == 0 -> {
                    view.setBackgroundResource(R.drawable.letter_select)
                    letterStates[index]++
                }
                letterStates[index] == 2 -> {
                    view.setBackgroundResource(R.drawable.letter_select_found)
                    letterStates[index]++
                }
                else -> {
                    if (letterStates[index] == 1) {
                        view.setBackgroundResource(0)
                    } else {
                        view.setBackgroundResource(R.drawable.letter_found)
                    }
                    letterStates[index]--
                }
            }
        }

        // Checks if there is a word found
        val selectedIndexes = letterStates.withIndex().filter { it.value == 1 || it.value == 3 }. map { it.index }
        val wordFound = wordBankSearch.filterValues { Arrays.equals(it.toIntArray(), selectedIndexes.toIntArray()) }
        if (wordFound.isNotEmpty()) {
            foundWord(wordBank.indexOf(wordFound.keys.first()))
        }
    }

    /**
     * The onClick of the Play Again button, which properly animates the view and resets the game
     */
    private fun playAgain() {
        setupGame()

        // Animates the change of color of the board and shows the right content
        animateColorBackground(500, gameBoard, R.color.white)
        animateHide(gameBoardFinished)
        animateShow(gameBoardContent)

        // Animates the height change of the game information top section and the game board
        val anim = ValueAnimator.ofInt(0, 143)
        val topPadding = dpToPx(20)
        anim.addUpdateListener { valueAnimator ->
            val `val` = valueAnimator.animatedValue as Int
            val layoutParams = gameInfo.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.height = `val`
            layoutParams.setMargins(
                0,
                ((`val`.toFloat() / 143.0) * topPadding.toFloat()).toInt(),
                0,
                0
            )
            gameInfo.layoutParams = layoutParams
            gameInfo.visibility = View.VISIBLE
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override
            fun onAnimationEnd(animation: Animator) {
                val layoutParams = gameInfo.layoutParams
                layoutParams.height = 143
                gameInfo.layoutParams = layoutParams
            }
        })
        anim.duration = 500
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.start()
    }

    /**
     * The callback when the game is finished, it stops the time and animates the view to dismiss the game information
     */
    private fun finishedGame() {
        // Stops the chronometer, get the elapsed seconds and displays it
        chronometer.stop()

        val elapsedSeconds = (SystemClock.elapsedRealtime() - chronometer.base) / 1000

        val finishDescription: TextView = findViewById(R.id.finishDescription)
        finishDescription.text = String.format(resources.getString(R.string.finished_message), elapsedSeconds)

        // Updates the best time if the time is better than the previous one
        var bestTime = getSharedPrefString("bestTime") ?: ""
        if (bestTime == "" || bestTime.toInt() > elapsedSeconds.toInt()) {
            bestTime = elapsedSeconds.toString()
        }
        storeSharedPref("bestTime", bestTime)

        // Animates the change of color of the board and shows the right content
        animateColorBackground(500, gameBoard, R.color.colorAccent)
        animateHide(gameBoardContent)
        animateShow(gameBoardFinished)

        // Animates the height change of the game information top section and the game board
        val anim = ValueAnimator.ofInt(gameInfo.measuredHeight, 0)
        val topPadding = (gameInfo.layoutParams as ConstraintLayout.LayoutParams).topMargin
        anim.addUpdateListener { valueAnimator ->
            val `val` = valueAnimator.animatedValue as Int
            val layoutParams = gameInfo.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.height = `val`
            layoutParams.setMargins(
                0,
                ((`val`.toFloat() / 143.0) * topPadding.toFloat()).toInt(),
                0,
                0
            )
            gameInfo.layoutParams = layoutParams
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override
            fun onAnimationEnd(animation: Animator) {
                val layoutParams = gameInfo.layoutParams
                layoutParams.height = 0
                gameInfo.layoutParams = layoutParams
                gameInfo.visibility = View.GONE
            }
        })
        anim.duration = 500
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.start()
    }

    /**
     * Called when a word is found
     *
     * @param wordIndex The index of the found word in the array for the word bank
     */
    private fun foundWord(wordIndex: Int) {
        // Checks if the word has not already been found and updates the score and the word bank
        if (!wordBankFound[wordIndex]) {
            score.text = (score.text.toString().toInt() + 1).toString()

            val child = wordBankLayout.getChildAt(wordIndex) as TextView
            child.paintFlags = child.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

            wordBankFound[wordIndex] = true
        }

        // Checks if there are any other words left
        if (!wordBankFound.contains(false)) {
            finishedGame()
        }

        // Updates the state of the letters and updates the view's look accordingly
        wordBankSearch[wordBank[wordIndex]]?.forEach {
            letterStates[it] = 2
            val letter = letters.getChildAt(it) as TextView
            letter.setBackgroundResource(R.drawable.letter_found)
            letter.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    /**
     * Helper function to show the specified view while animating it
     *
     * @param view The view to animate
     */
    private fun animateShow(view: View) {
        view.visibility = View.VISIBLE
        view.animate().alpha(1.0f)
    }

    /**
     * Helper function to hide the specified view while animating it
     *
     * @param view The view to animate
     */
    private fun animateHide(view: View) {
        view.animate().alpha(0.0f)
        view.visibility = View.GONE
    }

    /**
     * Animates the color of the background of a CardView
     *
     * @param time The length of the animation in milliseconds
     * @param view The CardView to be animated
     * @param colorResourceTo The resource color to transitioned to
     */
    private fun animateColorBackground(time: Long, view: CardView, colorResourceTo: Int){
        val colorFrom = view.cardBackgroundColor.defaultColor
        val colorTo = ContextCompat.getColor(this, colorResourceTo)
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.duration = time
        colorAnimation.addUpdateListener { animator -> view.setCardBackgroundColor(animator.animatedValue as Int) }
        colorAnimation.start()
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val WORDS =
            arrayListOf("Swift", "ObjectiveC", "Java", "Kotlin", "Variable", "Mobile")
        private val OPTIONAL_WORDS = arrayListOf("Android", "iOS", "Shopify", "Game", "Google", "Apple", "Phone", "Plants", "Tea")
    }
}