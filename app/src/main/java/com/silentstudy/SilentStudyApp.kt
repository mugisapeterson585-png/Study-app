package com.silentstudy

import android.app.Application
import com.silentstudy.ai.AiProviderManager
import com.silentstudy.audio.EarbudsAudioGuard
import com.silentstudy.audio.SilentStudyTtsManager
import com.silentstudy.data.local.AppDatabase
import com.silentstudy.data.repository.PreferencesRepository
import com.silentstudy.gestures.DeskTapSensorManager

class SilentStudyApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var earbudsGuard: EarbudsAudioGuard
        private set

    lateinit var ttsManager: SilentStudyTtsManager
        private set

    lateinit var deskTapSensor: DeskTapSensorManager
        private set

    lateinit var aiProviderManager: AiProviderManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        preferencesRepository = PreferencesRepository(this)
        earbudsGuard = EarbudsAudioGuard(this)
        ttsManager = SilentStudyTtsManager(this, earbudsGuard)
        deskTapSensor = DeskTapSensorManager(this)
        aiProviderManager = AiProviderManager()
    }

    override fun onTerminate() {
        earbudsGuard.unregister()
        ttsManager.shutdown()
        super.onTerminate()
    }
}
