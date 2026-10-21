package com.silentstudy.controller

import android.content.Context
import com.silentstudy.SilentStudyApp
import com.silentstudy.data.model.Answer
import com.silentstudy.data.model.AnswerBlock
import com.silentstudy.data.model.AnswerPart
import com.silentstudy.data.model.TableData
import com.silentstudy.gestures.DeskGesture
import com.silentstudy.gestures.GestureAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class PlaybackController(private val app: SilentStudyApp) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Active playback state
    private val _currentPlayingAnswer = MutableStateFlow<Answer?>(null)
    val currentPlayingAnswer: StateFlow<Answer?> = _currentPlayingAnswer.asStateFlow()

    private val _currentPartIndex = MutableStateFlow(0)
    val currentPartIndex: StateFlow<Int> = _currentPartIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _showAfterAnswerMenu = MutableStateFlow(false)
    val showAfterAnswerMenu: StateFlow<Boolean> = _showAfterAnswerMenu.asStateFlow()

    private val _afterMenuSeconds = MutableStateFlow(5)
    val afterMenuSeconds: StateFlow<Int> = _afterMenuSeconds.asStateFlow()

    // Last recognized gesture status line
    private val _lastGestureText = MutableStateFlow("Ready for desk taps")
    val lastGestureText: StateFlow<String> = _lastGestureText.asStateFlow()

    // Table Mode navigation state
    private val _isTableMode = MutableStateFlow(false)
    val isTableMode: StateFlow<Boolean> = _isTableMode.asStateFlow()

    private val _currentTableRow = MutableStateFlow(0)
    val currentTableRow: StateFlow<Int> = _currentTableRow.asStateFlow()

    private val _readByColumn = MutableStateFlow(false)
    val readByColumn: StateFlow<Boolean> = _readByColumn.asStateFlow()

    // Session stats
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    private val _answersHeardCount = MutableStateFlow(0)
    val answersHeardCount: StateFlow<Int> = _answersHeardCount.asStateFlow()

    // 5-Tap Number Entry Mode State
    private var isNumberEntryMode = false
    private val enteredDigits = mutableListOf<Int>()
    private var digitEntryJob: Job? = null
    private var numberConfirmJob: Job? = null

    // Sequential part playback job
    private var playbackJob: Job? = null
    private var afterMenuJob: Job? = null
    private var sessionTimerJob: Job? = null

    init {
        // Collect gestures globally in application/service scope
        scope.launch {
            app.deskTapSensor.gestures.collect { gesture ->
                handleGesture(gesture)
            }
        }

        // Restore last playback position on startup
        scope.launch {
            try {
                val lastPos = app.preferencesRepository.lastPlaybackPosition.first()
                if (lastPos.first > 0) {
                    val saved = withContext(Dispatchers.IO) {
                        app.database.answerDao().getAnswerById(lastPos.first)
                    }
                    if (saved != null) {
                        _currentPlayingAnswer.value = saved
                        val maxIdx = if (saved.answerParts.isNotEmpty()) saved.answerParts.size - 1 else (saved.blocks.size - 1).coerceAtLeast(0)
                        _currentPartIndex.value = lastPos.second.coerceIn(0, maxIdx)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun startSession() {
        if (_isSessionActive.value) return
        _isSessionActive.value = true
        com.silentstudy.service.SilentStudyService.startService(app)
        sessionTimerJob?.cancel()
        sessionTimerJob = scope.launch {
            while (_isSessionActive.value) {
                delay(1000)
                _sessionDurationSeconds.value += 1
            }
        }
    }

    fun stopSession() {
        _isSessionActive.value = false
        sessionTimerJob?.cancel()
        stop()
        com.silentstudy.service.SilentStudyService.stopService(app)
    }

    /**
     * Central gesture execution - called directly by the high-frequency sensor collector
     */
    private fun handleGesture(gesture: DeskGesture) {
        // Play earbud audio tick for every recognized gesture
        app.ttsManager.playEarbudTick()

        // If in 5-Tap Number Entry Mode, process taps as numerical digits
        if (isNumberEntryMode) {
            handleNumberEntryTap(gesture)
            return
        }

        scope.launch {
            // Read fresh answers from database synchronously
            val answers = withContext(Dispatchers.IO) {
                app.database.answerDao().getAllAnswersSync()
            }
            val gestureSettings = app.preferencesRepository.gestureSettings.first()
            val actionName = gestureSettings.customMappings[gesture.name] ?: defaultGestureMap()[gesture.name] ?: GestureAction.PLAY_PAUSE.name
            val action = try {
                GestureAction.valueOf(actionName)
            } catch (_: Exception) {
                GestureAction.PLAY_PAUSE
            }

            // Table Mode Gesture Override:
            if (_isTableMode.value && _currentPlayingAnswer.value?.table != null) {
                handleTableModeGesture(gesture, action)
                return@launch
            }

            when (action) {
                GestureAction.PLAY_PAUSE -> {
                    if (_showAfterAnswerMenu.value && _currentPlayingAnswer.value != null) {
                        dismissAfterMenu()
                        _lastGestureText.value = "${gesture.label} -> Repeat current answer"
                        playAnswer(_currentPlayingAnswer.value!!, 0)
                    } else if (_isPlaying.value) {
                        _lastGestureText.value = "${gesture.label} -> Paused"
                        pause()
                    } else if (_currentPlayingAnswer.value != null) {
                        _lastGestureText.value = "${gesture.label} -> Resumed #${_currentPlayingAnswer.value!!.id}"
                        playAnswer(_currentPlayingAnswer.value!!, _currentPartIndex.value)
                    } else if (answers.isNotEmpty()) {
                        val first = answers.first()
                        _lastGestureText.value = "${gesture.label} -> Playing #${first.id}"
                        playAnswer(first, 0)
                    } else {
                        _lastGestureText.value = "${gesture.label} -> No action: no answers saved"
                    }
                }

                GestureAction.NEXT_ANSWER -> {
                    dismissAfterMenu()
                    if (answers.isNotEmpty()) {
                        val currentId = _currentPlayingAnswer.value?.id
                        val currentIdx = answers.indexOfFirst { it.id == currentId }
                        val nextIdx = if (currentIdx != -1) (currentIdx + 1) % answers.size else 0
                        val nextAns = answers[nextIdx]
                        _lastGestureText.value = "${gesture.label} -> Next answer (#${nextAns.id})"
                        playAnswer(nextAns, 0)
                    } else {
                        _lastGestureText.value = "${gesture.label} -> No action: no answers saved"
                    }
                }

                GestureAction.PREV_ANSWER -> {
                    dismissAfterMenu()
                    if (answers.isNotEmpty()) {
                        val currentId = _currentPlayingAnswer.value?.id
                        val currentIdx = answers.indexOfFirst { it.id == currentId }
                        val prevIdx = if (currentIdx > 0) currentIdx - 1 else answers.size - 1
                        val prevAns = answers[prevIdx]
                        _lastGestureText.value = "${gesture.label} -> Previous answer (#${prevAns.id})"
                        playAnswer(prevAns, 0)
                    } else {
                        _lastGestureText.value = "${gesture.label} -> No action: no answers saved"
                    }
                }

                GestureAction.REPEAT -> {
                    dismissAfterMenu()
                    _currentPlayingAnswer.value?.let { ans ->
                        _lastGestureText.value = "${gesture.label} -> Repeat #${ans.id}"
                        playAnswer(ans, 0)
                    } ?: run {
                        _lastGestureText.value = "${gesture.label} -> No current answer to repeat"
                    }
                }

                GestureAction.REPEAT_LAST_PART -> {
                    dismissAfterMenu()
                    _currentPlayingAnswer.value?.let { ans ->
                        _lastGestureText.value = "${gesture.label} -> Repeat last part"
                        repeatLastPart()
                    } ?: run {
                        _lastGestureText.value = "${gesture.label} -> No active part to repeat"
                    }
                }

                GestureAction.STOP_SILENT -> {
                    _lastGestureText.value = "${gesture.label} -> Stop and silent"
                    stop()
                }

                GestureAction.JUMP_TO_N -> {
                    // Enter 5-tap number entry mode
                    startNumberEntryMode(gesture)
                }

                GestureAction.CYCLE_SPEED -> {
                    val audioSettings = app.preferencesRepository.audioSettings.first()
                    val newRate = when {
                        audioSettings.speechRate < 1.0f -> 1.0f
                        audioSettings.speechRate < 1.25f -> 1.25f
                        audioSettings.speechRate < 1.5f -> 1.5f
                        else -> 0.85f
                    }
                    app.preferencesRepository.setSpeechRate(newRate)
                    _lastGestureText.value = "${gesture.label} -> Speech rate: ${newRate}x"
                    app.ttsManager.spokenPrompt("Speed ${newRate}x")
                }

                GestureAction.SHORT_SUMMARY -> {
                    _currentPlayingAnswer.value?.let { ans ->
                        _lastGestureText.value = "${gesture.label} -> Reading direct answer"
                        app.ttsManager.speak(ans.shortAnswer, "short_answer")
                    } ?: run {
                        _lastGestureText.value = "${gesture.label} -> No answer selected"
                    }
                }

                GestureAction.EXPLAIN_SIMPLER -> {
                    _currentPlayingAnswer.value?.let { ans ->
                        if (ans.simpleExplanation.isNotEmpty() && !ans.simpleExplanation.contains("(Generated on demand)")) {
                            _lastGestureText.value = "${gesture.label} -> Simple explanation"
                            app.ttsManager.speak(ans.simpleExplanation, "simple_explanation")
                        } else {
                            _lastGestureText.value = "${gesture.label} -> Generating explanation..."
                            scope.launch {
                                val aiSettings = app.preferencesRepository.aiSettings.first()
                                val exp = app.aiProviderManager.generateSimpleExplanation(
                                    aiSettings.provider, aiSettings.apiKey, aiSettings.model,
                                    aiSettings.baseUrl, ans.questionText, ans.shortAnswer
                                )
                                val updated = ans.copy(simpleExplanation = exp)
                                withContext(Dispatchers.IO) {
                                    app.database.answerDao().updateAnswer(updated)
                                }
                                _currentPlayingAnswer.value = updated
                                app.ttsManager.speak(exp, "simple_explanation")
                            }
                        }
                    } ?: run {
                        _lastGestureText.value = "${gesture.label} -> No answer selected"
                    }
                }

                else -> {
                    _lastGestureText.value = "${gesture.label} -> ${action.displayName}"
                }
            }
        }
    }

    private fun handleTableModeGesture(gesture: DeskGesture, action: GestureAction) {
        val ans = _currentPlayingAnswer.value ?: return
        val table = ans.table ?: return

        when (gesture) {
            DeskGesture.SINGLE_TAP -> {
                if (_isPlaying.value) {
                    _lastGestureText.value = "1 tap -> Table paused"
                    pause()
                } else {
                    _lastGestureText.value = "1 tap -> Table resume row ${_currentTableRow.value + 1}"
                    readTableRow(table, _currentTableRow.value)
                }
            }
            DeskGesture.DOUBLE_TAP -> {
                val next = (_currentTableRow.value + 1) % maxOf(1, table.rows.size)
                _currentTableRow.value = next
                _lastGestureText.value = "2 taps -> Next row (${next + 1}/${table.rows.size})"
                readTableRow(table, next)
            }
            DeskGesture.TRIPLE_TAP -> {
                val prev = if (_currentTableRow.value > 0) _currentTableRow.value - 1 else table.rows.size - 1
                _currentTableRow.value = prev
                _lastGestureText.value = "3 taps -> Prev row (${prev + 1}/${table.rows.size})"
                readTableRow(table, prev)
            }
            DeskGesture.TAP_PAUSE_TAP -> {
                _lastGestureText.value = "Tap-pause-tap -> Repeat row ${_currentTableRow.value + 1}"
                readTableRow(table, _currentTableRow.value)
            }
            DeskGesture.FOUR_TAPS -> {
                _lastGestureText.value = "4 taps -> Stop table reading"
                _isTableMode.value = false
                stop()
            }
            else -> {
                _lastGestureText.value = "${gesture.label} -> Table row ${_currentTableRow.value + 1}"
            }
        }
    }

    /**
     * 5-Tap Number Entry Mode Implementation:
     * After 5 taps, user enters digits one by one.
     * Wait 1 second for next digit, wait 2 seconds of silence to confirm.
     * 10 taps = 0.
     */
    private fun startNumberEntryMode(gesture: DeskGesture) {
        isNumberEntryMode = true
        enteredDigits.clear()
        _lastGestureText.value = "${gesture.label} -> Enter answer number (tap digits)"
        app.ttsManager.spokenPrompt("Enter number")
    }

    private fun handleNumberEntryTap(gesture: DeskGesture) {
        val count = when (gesture) {
            DeskGesture.SINGLE_TAP -> 1
            DeskGesture.DOUBLE_TAP -> 2
            DeskGesture.TRIPLE_TAP -> 3
            DeskGesture.FOUR_TAPS -> 4
            DeskGesture.FIVE_TAPS -> 5
            DeskGesture.SIX_TAPS -> 6
            else -> 1
        }
        val digit = if (count >= 10) 0 else count
        enteredDigits.add(digit)

        digitEntryJob?.cancel()
        numberConfirmJob?.cancel()

        _lastGestureText.value = "Digit entered: $digit (Number: ${enteredDigits.joinToString("")})"

        // 2 seconds of silence confirms number entry
        numberConfirmJob = scope.launch {
            delay(2000)
            confirmNumberEntry()
        }
    }

    private fun confirmNumberEntry() {
        isNumberEntryMode = false
        if (enteredDigits.isEmpty()) {
            _lastGestureText.value = "Number entry cancelled"
            return
        }

        val targetNum = enteredDigits.joinToString("").toLongOrNull() ?: 1L
        enteredDigits.clear()
        _lastGestureText.value = "Jumping to answer #$targetNum"
        app.ttsManager.spokenPrompt("Answer $targetNum")

        scope.launch {
            val target = withContext(Dispatchers.IO) {
                app.database.answerDao().getAnswerById(targetNum)
            }
            if (target != null) {
                playAnswer(target, 0)
            } else {
                _lastGestureText.value = "Answer #$targetNum not found"
                app.ttsManager.spokenPrompt("Answer not found")
            }
        }
    }

    /**
     * Start playing an Answer in parts with 600ms pauses in a clear voice
     */
    fun playAnswer(answer: Answer, startPartIndex: Int = 0) {
        playbackJob?.cancel()
        dismissAfterMenu()
        _currentPlayingAnswer.value = answer
        _isPlaying.value = true

        playbackJob = scope.launch {
            val audioSettings = app.preferencesRepository.audioSettings.first()
            val speechRate = audioSettings.speechRate

            // If answer has Table Data and user requested Table reading
            if (_isTableMode.value && answer.table != null) {
                readTableCompletely(answer.table)
                return@launch
            }

            // If answer has Graph Data and no parts
            if (answer.graph != null && answer.answerParts.isEmpty()) {
                readGraphCompletely(answer.graph)
                return@launch
            }

            // Normal Parts Playback:
            val parts = answer.answerParts.ifEmpty {
                answer.blocks.map { AnswerPart(display = it.display, spoken = it.spoken) }
            }

            val startIndex = startPartIndex.coerceIn(0, maxOf(0, parts.size - 1))
            for (i in startIndex until parts.size) {
                if (!_isPlaying.value) break
                _currentPartIndex.value = i
                val part = parts[i]

                // Save last played position
                withContext(Dispatchers.IO) {
                    app.preferencesRepository.saveLastPlaybackPosition(answer.id, i)
                }

                val spokenText = if (part.label.isNotBlank() && parts.size > 1) {
                    "${part.label}. ${part.spoken}"
                } else {
                    part.spoken
                }

                // Slower rate for formulas/parts (~0.85x of normal)
                val partRate = if (audioSettings.mathSlowdown) speechRate * 0.90f else speechRate
                val completer = CompletableDeferred<Unit>()
                app.ttsManager.onUtteranceFinished = { id ->
                    if (id.startsWith("part_$i")) {
                        completer.complete(Unit)
                    }
                }

                app.ttsManager.speak(spokenText, "part_${i}_${System.currentTimeMillis()}", speechRate = partRate)

                // Wait for part to finish speaking
                completer.await()

                // Short 600ms pause between sequential parts
                delay(600)
            }

            // If answer has structured table, announce it
            if (answer.table != null && _isPlaying.value) {
                delay(400)
                app.ttsManager.spokenPrompt("Table data included. Tap twice to enter table mode.")
            }

            // Finished all parts
            _isPlaying.value = false
            _answersHeardCount.value += 1
            triggerAfterAnswerMenu()
        }
    }

    /**
     * Repeat only the part that was just spoken
     */
    fun repeatLastPart() {
        val ans = _currentPlayingAnswer.value ?: return
        val parts = ans.answerParts.ifEmpty {
            ans.blocks.map { AnswerPart(display = it.display, spoken = it.spoken) }
        }
        val idx = _currentPartIndex.value.coerceIn(0, maxOf(0, parts.size - 1))
        val part = parts.getOrNull(idx) ?: return

        scope.launch {
            val audioSettings = app.preferencesRepository.audioSettings.first()
            val text = if (part.label.isNotBlank()) "${part.label}. ${part.spoken}" else part.spoken
            app.ttsManager.speak(text, "repeat_part_$idx", speechRate = audioSettings.speechRate * 0.90f)
        }
    }

    /**
     * Read table row by row
     */
    fun startTableReading(answer: Answer) {
        _isTableMode.value = true
        _currentPlayingAnswer.value = answer
        _currentTableRow.value = 0
        answer.table?.let { table ->
            scope.launch {
                readTableCompletely(table)
            }
        }
    }

    private suspend fun readTableCompletely(table: TableData) {
        val audioSettings = app.preferencesRepository.audioSettings.first()
        // Say title and headers once
        val headerText = "${table.title}. Columns: " + table.headers.joinToString(", ")
        val completer = CompletableDeferred<Unit>()
        app.ttsManager.onUtteranceFinished = { completer.complete(Unit) }
        app.ttsManager.speak(headerText, "table_header", speechRate = audioSettings.speechRate)
        completer.await()
        delay(600)

        // Read row by row
        for (rowIndex in _currentTableRow.value until table.rows.size) {
            if (!_isPlaying.value || !_isTableMode.value) break
            _currentTableRow.value = rowIndex
            readTableRow(table, rowIndex)
            delay(700)
        }
    }

    fun readTableRow(table: TableData, rowIndex: Int) {
        val row = table.rows.getOrNull(rowIndex) ?: return
        val builder = StringBuilder("Row ${rowIndex + 1}. ")
        for (i in 0 until minOf(table.headers.size, row.size)) {
            builder.append("${table.headers[i]}, ${row[i]}. ")
        }
        scope.launch {
            val audioSettings = app.preferencesRepository.audioSettings.first()
            app.ttsManager.speak(builder.toString(), "table_row_$rowIndex", speechRate = audioSettings.speechRate)
        }
    }

    private suspend fun readGraphCompletely(graph: GraphData) {
        val audioSettings = app.preferencesRepository.audioSettings.first()
        val speechRate = audioSettings.speechRate

        // 1. Title & Type
        app.ttsManager.spokenPrompt("${graph.type} chart: ${graph.title}")
        delay(700)

        // 2. Axes
        if (graph.xAxis != null || graph.yAxis != null) {
            val axesText = "X axis: ${graph.xAxis?.label ?: "value"} in ${graph.xAxis?.unit ?: "units"}. Y axis: ${graph.yAxis?.label ?: "value"} in ${graph.yAxis?.unit ?: "units"}."
            app.ttsManager.spokenPrompt(axesText)
            delay(700)
        }

        // 3. Key Values
        if (graph.keyPoints.isNotEmpty()) {
            val pointsText = "Key values: " + graph.keyPoints.joinToString(". ")
            app.ttsManager.spokenPrompt(pointsText)
            delay(700)
        }

        // 4. Trend
        if (graph.trend.isNotBlank()) {
            app.ttsManager.spokenPrompt("Trend: ${graph.trend}")
        }
    }

    fun pause() {
        playbackJob?.cancel()
        _isPlaying.value = false
        app.ttsManager.stop()
    }

    fun stop() {
        playbackJob?.cancel()
        _isPlaying.value = false
        _isTableMode.value = false
        app.ttsManager.stop()
        dismissAfterMenu()
    }

    private fun triggerAfterAnswerMenu() {
        afterMenuJob?.cancel()
        _showAfterAnswerMenu.value = true
        _afterMenuSeconds.value = 5

        afterMenuJob = scope.launch {
            val audioSettings = app.preferencesRepository.audioSettings.first()
            if (audioSettings.spokenMenuEnabled) {
                app.ttsManager.spokenPrompt("Tap once to repeat. Tap twice for next answer.")
            }
            while (_afterMenuSeconds.value > 0) {
                delay(1000)
                _afterMenuSeconds.value -= 1
            }
            _showAfterAnswerMenu.value = false
        }
    }

    fun dismissAfterMenu() {
        afterMenuJob?.cancel()
        _showAfterAnswerMenu.value = false
    }
}
