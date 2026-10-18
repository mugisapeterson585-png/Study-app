/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import { 
  Camera, 
  BookOpen, 
  GraduationCap, 
  Settings as SettingsIcon,
  Code2,
  FileCode
} from 'lucide-react';
import { 
  AnswerItem, 
  AppSettings, 
  StudySessionState, 
  GestureType, 
  GestureAction 
} from './types';
import { AppStorage, DEFAULT_SETTINGS } from './data/storage';
import { EarbudsGuard } from './audio/EarbudsAudioGuard';
import { AudioPlayer } from './audio/AudioPlayer';
import { DeskTapEngine } from './gestures/DeskTapEngine';

import { StatusPill } from './components/StatusPill';
import { CaptureView } from './views/CaptureView';
import { AnswersView } from './views/AnswersView';
import { StudyView } from './views/StudyView';
import { SettingsView } from './views/SettingsView';
import { DeskScreenOffMode } from './views/DeskScreenOffMode';
import { AnswerDetailModal } from './components/AnswerDetailModal';
import { NativeAndroidExport } from './components/NativeAndroidExport';

export default function App() {
  const [activeTab, setActiveTab] = useState<'capture' | 'answers' | 'study' | 'settings' | 'export'>('capture');
  const [answers, setAnswers] = useState<AnswerItem[]>([]);
  const [settings, setSettings] = useState<AppSettings>(DEFAULT_SETTINGS);
  const [selectedAnswer, setSelectedAnswer] = useState<AnswerItem | null>(null);
  const [deletedUndoItem, setDeletedUndoItem] = useState<AnswerItem | null>(null);
  const undoTimeoutRef = useRef<any>(null);

  // Hardware states
  const [hasEarbuds, setHasEarbuds] = useState<boolean>(true);
  const [isSensorActive, setIsSensorActive] = useState<boolean>(true);
  const [isOnline, setIsOnline] = useState<boolean>(navigator.onLine);
  const [isDeskModeOpen, setIsDeskModeOpen] = useState<boolean>(false);

  // Study session
  const [sessionState, setSessionState] = useState<StudySessionState>({
    isActive: false,
    startTime: null,
    durationSeconds: 0,
    answersHeard: 0,
    pomodoroStage: 'idle',
    pomodoroTimeRemaining: 25 * 60,
  });

  // Initial load
  useEffect(() => {
    const loadedSettings = AppStorage.getSettings();
    setSettings(loadedSettings);
    AudioPlayer.setSettings(loadedSettings);

    const loadedAnswers = AppStorage.getAnswers();
    setAnswers(loadedAnswers);

    // Apply dark mode if set
    if (loadedSettings.isDarkMode) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }

    // Subscribe to Earbuds guard
    const unsubEarbuds = EarbudsGuard.subscribe(status => {
      setHasEarbuds(status.hasEarbudsConnected);
    });

    // Start gesture sensor
    DeskTapEngine.startListening();
    setIsSensorActive(true);

    // Online / Offline tracking
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      unsubEarbuds();
      DeskTapEngine.stopListening();
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  // Session duration timer loop
  useEffect(() => {
    let interval: any = null;
    if (sessionState.isActive) {
      interval = setInterval(() => {
        setSessionState(prev => ({
          ...prev,
          durationSeconds: prev.durationSeconds + 1,
        }));
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [sessionState.isActive]);

  // Global Desk Gesture Handler
  useEffect(() => {
    const unsub = DeskTapEngine.subscribeGestures((gesture: GestureType) => {
      const action: GestureAction = settings.gestureMappings[gesture] || 'play_pause';

      if (action === 'play_pause') {
        const current = AudioPlayer.getCurrentAnswer() || answers[0];
        if (current) {
          AudioPlayer.togglePlayPause();
        }
      } else if (action === 'next_answer') {
        handleNextAnswer();
      } else if (action === 'prev_answer') {
        handlePrevAnswer();
      } else if (action === 'repeat') {
        const current = AudioPlayer.getCurrentAnswer() || answers[0];
        if (current) AudioPlayer.playAnswer(current);
      } else if (action === 'short_summary') {
        const current = AudioPlayer.getCurrentAnswer() || answers[0];
        if (current) AudioPlayer.playAnswer(current, 'short');
      } else if (action === 'explain_simpler') {
        const current = AudioPlayer.getCurrentAnswer() || answers[0];
        if (current) AudioPlayer.playAnswer(current, 'simple');
      } else if (action === 'stop_silent') {
        AudioPlayer.stopImmediate();
      } else if (action === 'cycle_speed') {
        const speeds = [0.75, 1.0, 1.25, 1.5, 2.0];
        const nextIdx = (speeds.indexOf(settings.playbackSpeed) + 1) % speeds.length;
        const newSpeed = speeds[nextIdx];
        const updated = { ...settings, playbackSpeed: newSpeed };
        setSettings(updated);
        AppStorage.saveSettings(updated);
        AudioPlayer.setSettings(updated);
        AudioPlayer.playGestureTick(nextIdx + 1);
      }
    });

    return () => unsub();
  }, [answers, settings]);

  const handleNextAnswer = () => {
    const current = AudioPlayer.getCurrentAnswer();
    if (!current || answers.length === 0) return;
    const currentIdx = answers.findIndex(a => a.id === current.id);
    const nextIdx = (currentIdx + 1) % answers.length;
    const nextAnswer = answers[nextIdx];
    AudioPlayer.playAnswer(nextAnswer);
    setSelectedAnswer(nextAnswer);
    setSessionState(s => ({ ...s, answersHeard: s.answersHeard + 1 }));
  };

  const handlePrevAnswer = () => {
    const current = AudioPlayer.getCurrentAnswer();
    if (!current || answers.length === 0) return;
    const currentIdx = answers.findIndex(a => a.id === current.id);
    const prevIdx = (currentIdx - 1 + answers.length) % answers.length;
    const prevAnswer = answers[prevIdx];
    AudioPlayer.playAnswer(prevAnswer);
    setSelectedAnswer(prevAnswer);
  };

  const handleToggleEarbudsSimulation = () => {
    EarbudsGuard.setSimulatedConnection(!hasEarbuds);
  };

  const handleToggleSensor = () => {
    if (isSensorActive) {
      DeskTapEngine.stopListening();
      setIsSensorActive(false);
    } else {
      DeskTapEngine.startListening();
      setIsSensorActive(true);
    }
  };

  const handleToggleSession = () => {
    setSessionState(prev => ({
      ...prev,
      isActive: !prev.isActive,
      startTime: !prev.isActive ? Date.now() : prev.startTime,
    }));
  };

  const handleAnswerCreated = (newAnswer: AnswerItem) => {
    setAnswers(prev => [newAnswer, ...prev]);
    setSelectedAnswer(newAnswer);
    setSessionState(s => ({ ...s, answersHeard: s.answersHeard + 1 }));
  };

  const handleToggleStar = (id: number) => {
    AppStorage.toggleStar(id);
    setAnswers(AppStorage.getAnswers());
    if (selectedAnswer?.id === id) {
      setSelectedAnswer(prev => prev ? { ...prev, starred: !prev.starred } : null);
    }
  };

  const handleDeleteAnswer = (id: number) => {
    const deleted = AppStorage.deleteAnswer(id);
    if (deleted) {
      setAnswers(AppStorage.getAnswers());
      if (selectedAnswer?.id === id) setSelectedAnswer(null);

      // 10-second undo window
      setDeletedUndoItem(deleted);
      if (undoTimeoutRef.current) clearTimeout(undoTimeoutRef.current);
      undoTimeoutRef.current = setTimeout(() => {
        setDeletedUndoItem(null);
      }, 10000);
    }
  };

  const handleUndoDelete = () => {
    if (deletedUndoItem) {
      AppStorage.restoreAnswer(deletedUndoItem);
      setAnswers(AppStorage.getAnswers());
      setDeletedUndoItem(null);
      if (undoTimeoutRef.current) clearTimeout(undoTimeoutRef.current);
    }
  };

  const activeAiProfile = settings.profiles.find(p => p.id === settings.activeProfileId) || settings.profiles[0];
  const fallbackAiProfile = settings.profiles.find(p => p.id === settings.fallbackProfileId);

  return (
    <div className="min-h-screen flex flex-col transition-colors selection:bg-blue-500/20">
      {/* Top Status Pill Header */}
      <StatusPill
        hasEarbuds={hasEarbuds}
        onToggleEarbuds={handleToggleEarbudsSimulation}
        isSensorActive={isSensorActive}
        onToggleSensor={handleToggleSensor}
        sessionState={sessionState}
        isOnline={isOnline}
        settings={settings}
        onOpenDeskMode={() => setIsDeskModeOpen(true)}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-2 sm:p-4">
        {activeTab === 'capture' && (
          <CaptureView
            settings={settings}
            activeProfile={activeAiProfile}
            fallbackProfile={fallbackAiProfile}
            onAnswerCreated={handleAnswerCreated}
            onOpenSettingsAi={() => setActiveTab('settings')}
          />
        )}

        {activeTab === 'answers' && (
          <AnswersView
            answers={answers}
            onSelectAnswer={setSelectedAnswer}
            onPlayAnswer={(ans) => {
              setSelectedAnswer(ans);
              AudioPlayer.playAnswer(ans);
              setSessionState(s => ({ ...s, answersHeard: s.answersHeard + 1 }));
            }}
            onToggleStar={handleToggleStar}
            onDeleteAnswer={handleDeleteAnswer}
            onUndoDelete={handleUndoDelete}
            deletedItem={deletedUndoItem}
          />
        )}

        {activeTab === 'study' && (
          <StudyView
            answers={answers}
            sessionState={sessionState}
            onToggleSession={handleToggleSession}
            settings={settings}
            onSelectAnswer={setSelectedAnswer}
          />
        )}

        {activeTab === 'settings' && (
          <SettingsView
            settings={settings}
            onUpdateSettings={setSettings}
            onOpenAndroidExport={() => setActiveTab('export')}
          />
        )}

        {activeTab === 'export' && (
          <NativeAndroidExport />
        )}
      </main>

      {/* Answer Detail Modal */}
      {selectedAnswer && (
        <AnswerDetailModal
          answer={selectedAnswer}
          isOpen={true}
          onClose={() => setSelectedAnswer(null)}
          onToggleStar={handleToggleStar}
          onNextAnswer={handleNextAnswer}
        />
      )}

      {/* Desk Screen-Off / Library Sleep Mode */}
      {isDeskModeOpen && (
        <DeskScreenOffMode
          onExit={() => setIsDeskModeOpen(false)}
          currentAnswer={AudioPlayer.getCurrentAnswer() || answers[0] || null}
          onNextAnswer={handleNextAnswer}
          onPrevAnswer={handlePrevAnswer}
        />
      )}

      {/* Bottom Navigation Dock (Section 1: Capture, Answers, Study, Settings) */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 backdrop-blur-lg bg-[#E0E5EC]/90 dark:bg-[#2B2E35]/90 border-t border-slate-300/40 dark:border-slate-700/40 py-1.5 px-3">
        <div className="max-w-md mx-auto grid grid-cols-5 items-center gap-1">
          <button
            onClick={() => setActiveTab('capture')}
            className={`flex flex-col items-center justify-center py-1.5 rounded-2xl transition-all cursor-pointer ${
              activeTab === 'capture'
                ? 'neu-pressed text-blue-600 dark:text-blue-400 font-bold'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-800'
            }`}
          >
            <Camera size={20} />
            <span className="text-[10px] mt-0.5 tracking-tight">Capture</span>
          </button>

          <button
            onClick={() => setActiveTab('answers')}
            className={`flex flex-col items-center justify-center py-1.5 rounded-2xl transition-all cursor-pointer ${
              activeTab === 'answers'
                ? 'neu-pressed text-blue-600 dark:text-blue-400 font-bold'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-800'
            }`}
          >
            <BookOpen size={20} />
            <span className="text-[10px] mt-0.5 tracking-tight">Answers</span>
          </button>

          <button
            onClick={() => setActiveTab('study')}
            className={`flex flex-col items-center justify-center py-1.5 rounded-2xl transition-all cursor-pointer ${
              activeTab === 'study'
                ? 'neu-pressed text-blue-600 dark:text-blue-400 font-bold'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-800'
            }`}
          >
            <GraduationCap size={20} />
            <span className="text-[10px] mt-0.5 tracking-tight">Study</span>
          </button>

          <button
            onClick={() => setActiveTab('settings')}
            className={`flex flex-col items-center justify-center py-1.5 rounded-2xl transition-all cursor-pointer ${
              activeTab === 'settings'
                ? 'neu-pressed text-blue-600 dark:text-blue-400 font-bold'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-800'
            }`}
          >
            <SettingsIcon size={20} />
            <span className="text-[10px] mt-0.5 tracking-tight">Settings</span>
          </button>

          <button
            onClick={() => setActiveTab('export')}
            className={`flex flex-col items-center justify-center py-1.5 rounded-2xl transition-all cursor-pointer ${
              activeTab === 'export'
                ? 'neu-pressed text-blue-600 dark:text-blue-400 font-bold'
                : 'text-slate-500 dark:text-slate-400 hover:text-slate-800'
            }`}
            title="Inspect Android Kotlin Architecture"
          >
            <FileCode size={20} />
            <span className="text-[10px] mt-0.5 tracking-tight">Android</span>
          </button>
        </div>
      </nav>
    </div>
  );
}
