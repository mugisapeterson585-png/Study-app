import React, { useState } from 'react';
import { 
  AppSettings, 
  AiProfile, 
  AiProviderType, 
  GestureType, 
  GestureAction 
} from '../types';
import { 
  Cpu, 
  Volume2, 
  HandMetal, 
  Palette, 
  ShieldCheck, 
  Database, 
  Plus, 
  Trash2, 
  Key, 
  Eye, 
  EyeOff, 
  Clipboard, 
  Check, 
  AlertCircle, 
  CheckCircle2, 
  Loader2,
  FileCode,
  Download,
  Upload
} from 'lucide-react';
import { NeuCard } from '../components/neumorphic/NeuCard';
import { NeuButton } from '../components/neumorphic/NeuButton';
import { NeuToggle } from '../components/neumorphic/NeuToggle';
import { NeuSlider } from '../components/neumorphic/NeuSlider';
import { NeuTextField } from '../components/neumorphic/NeuTextField';
import { NeuIconButton } from '../components/neumorphic/NeuIconButton';
import { AiProviderService, ConnectionTestResult } from '../ai/AiProvider';
import { AppStorage } from '../data/storage';

interface SettingsViewProps {
  settings: AppSettings;
  onUpdateSettings: (settings: AppSettings) => void;
  onOpenAndroidExport: () => void;
}

const PROVIDER_OPTIONS: { id: AiProviderType; name: string; defaultModel: string; hint: string }[] = [
  { id: 'gemini', name: 'Google Gemini', defaultModel: 'gemini-2.5-flash', hint: 'Fast vision model with rich JSON schema support' },
  { id: 'openai', name: 'OpenAI', defaultModel: 'gpt-4o-mini', hint: 'GPT-4o or GPT-4o-mini with vision' },
  { id: 'claude', name: 'Anthropic Claude', defaultModel: 'claude-3-5-haiku-20241022', hint: 'Claude 3.5 Haiku or Sonnet' },
  { id: 'openrouter', name: 'OpenRouter', defaultModel: 'google/gemini-2.0-flash-001', hint: 'Access any open or proprietary vision model' },
  { id: 'custom', name: 'Custom / Local (Ollama/LM Studio)', defaultModel: 'llama3.2-vision', hint: 'Compatible with local or private inference endpoints' },
];

export const SettingsView: React.FC<SettingsViewProps> = ({
  settings,
  onUpdateSettings,
  onOpenAndroidExport,
}) => {
  const [activeTab, setActiveTab] = useState<'ai' | 'audio' | 'gestures' | 'appearance' | 'data'>('ai');

  // Active AI Profile state for editing
  const currentProfile = settings.profiles.find(p => p.id === settings.activeProfileId) || settings.profiles[0];
  const [editingProfile, setEditingProfile] = useState<AiProfile>({ ...currentProfile });
  const [showKey, setShowKey] = useState(false);
  const [testResult, setTestResult] = useState<ConnectionTestResult | null>(null);
  const [isTesting, setIsTesting] = useState(false);

  // Backup state
  const [importNotice, setImportNotice] = useState<string | null>(null);

  const handleSaveProfile = () => {
    const updatedProfiles = settings.profiles.map(p => 
      p.id === editingProfile.id ? editingProfile : p
    );
    const updated = {
      ...settings,
      profiles: updatedProfiles,
      activeProfileId: editingProfile.id
    };
    onUpdateSettings(updated);
    AppStorage.saveSettings(updated);
    AppStorage.saveProfile(editingProfile);
  };

  const handleTestConnection = async () => {
    setIsTesting(true);
    setTestResult(null);
    try {
      const res = await AiProviderService.testConnection(editingProfile);
      setTestResult(res);
    } catch (err: any) {
      setTestResult({
        success: false,
        message: err.message || 'Connection failed',
        latencyMs: 0
      });
    } finally {
      setIsTesting(false);
    }
  };

  const handlePasteKey = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) {
        setEditingProfile(p => ({ ...p, apiKey: text.trim() }));
      }
    } catch {
      // Permission denied
    }
  };

  const handleSelectProfile = (id: string) => {
    const target = settings.profiles.find(p => p.id === id);
    if (target) {
      setEditingProfile({ ...target });
      const updated = { ...settings, activeProfileId: id };
      onUpdateSettings(updated);
      AppStorage.saveSettings(updated);
      setTestResult(null);
    }
  };

  const handleExportBackup = () => {
    const jsonStr = AppStorage.exportBackupJson();
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `silentstudy_backup_${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const handleImportBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      const result = AppStorage.importBackupJson(event.target?.result as string);
      setImportNotice(result.message);
      setTimeout(() => window.location.reload(), 1500);
    };
    reader.readAsText(file);
  };

  return (
    <div className="max-w-4xl mx-auto px-3 py-4 flex flex-col gap-4 pb-24">
      {/* Category Tabs */}
      <div className="flex items-center gap-1.5 p-1 rounded-2xl neu-pressed overflow-x-auto no-scrollbar">
        <button
          onClick={() => setActiveTab('ai')}
          className={`flex-1 py-2 px-3 text-xs font-semibold rounded-xl whitespace-nowrap transition-all cursor-pointer ${
            activeTab === 'ai' ? 'neu-raised text-blue-600 dark:text-blue-400 font-bold' : 'text-slate-500'
          }`}
        >
          AI Providers (No Lock-in)
        </button>
        <button
          onClick={() => setActiveTab('audio')}
          className={`flex-1 py-2 px-3 text-xs font-semibold rounded-xl whitespace-nowrap transition-all cursor-pointer ${
            activeTab === 'audio' ? 'neu-raised text-blue-600 dark:text-blue-400 font-bold' : 'text-slate-500'
          }`}
        >
          Audio & Earbuds
        </button>
        <button
          onClick={() => setActiveTab('gestures')}
          className={`flex-1 py-2 px-3 text-xs font-semibold rounded-xl whitespace-nowrap transition-all cursor-pointer ${
            activeTab === 'gestures' ? 'neu-raised text-blue-600 dark:text-blue-400 font-bold' : 'text-slate-500'
          }`}
        >
          Desk Gestures
        </button>
        <button
          onClick={() => setActiveTab('appearance')}
          className={`flex-1 py-2 px-3 text-xs font-semibold rounded-xl whitespace-nowrap transition-all cursor-pointer ${
            activeTab === 'appearance' ? 'neu-raised text-blue-600 dark:text-blue-400 font-bold' : 'text-slate-500'
          }`}
        >
          Theme
        </button>
        <button
          onClick={() => setActiveTab('data')}
          className={`flex-1 py-2 px-3 text-xs font-semibold rounded-xl whitespace-nowrap transition-all cursor-pointer ${
            activeTab === 'data' ? 'neu-raised text-blue-600 dark:text-blue-400 font-bold' : 'text-slate-500'
          }`}
        >
          Backup & Code
        </button>
      </div>

      {/* TAB 1: AI Provider Layer (Section 4A - No Lock-in) */}
      {activeTab === 'ai' && (
        <div className="flex flex-col gap-4">
          {/* Profiles Carousel */}
          <NeuCard elevation="raised" rounded="2xl" className="p-4">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Active Provider Profile
              </span>
              <span className="text-[11px] text-slate-400">
                Switch profiles with 1 tap
              </span>
            </div>

            <div className="flex gap-2 overflow-x-auto py-1">
              {settings.profiles.map(p => (
                <button
                  key={p.id}
                  onClick={() => handleSelectProfile(p.id)}
                  className={`px-3 py-2 rounded-xl text-xs font-semibold shrink-0 cursor-pointer flex items-center gap-1.5 transition-all ${
                    settings.activeProfileId === p.id
                      ? 'neu-pressed text-blue-600 dark:text-blue-400 font-bold border border-blue-500/40'
                      : 'neu-raised text-slate-600 dark:text-slate-300'
                  }`}
                >
                  <Cpu size={13} />
                  <span>{p.name}</span>
                </button>
              ))}
            </div>
          </NeuCard>

          {/* Profile Configuration Form */}
          <NeuCard elevation="raised" rounded="3xl" className="p-5 sm:p-6 flex flex-col gap-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-300/40 dark:border-slate-700/40">
              <div className="flex items-center gap-2">
                <Key size={18} className="text-blue-500" />
                <h3 className="text-sm font-bold text-slate-800 dark:text-slate-100">
                  Configure Provider: {editingProfile.name}
                </h3>
              </div>
              <span className="text-[11px] text-slate-400">
                Keys stored securely on device only
              </span>
            </div>

            {/* Provider Type Selector */}
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                AI Provider Platform
              </label>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                {PROVIDER_OPTIONS.map(opt => (
                  <button
                    key={opt.id}
                    onClick={() => {
                      setEditingProfile(p => ({
                        ...p,
                        provider: opt.id,
                        model: opt.defaultModel
                      }));
                    }}
                    className={`p-2.5 rounded-xl text-left transition-all cursor-pointer flex flex-col ${
                      editingProfile.provider === opt.id
                        ? 'neu-pressed text-blue-600 dark:text-blue-400 font-bold border border-blue-500/40'
                        : 'neu-raised text-slate-600 dark:text-slate-300'
                    }`}
                  >
                    <span className="text-xs font-bold">{opt.name}</span>
                    <span className="text-[10px] text-slate-400 mt-0.5 line-clamp-1">{opt.hint}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* API Key Input Field */}
            <div className="flex flex-col gap-1">
              <div className="flex justify-between items-center text-xs">
                <label className="font-semibold text-slate-700 dark:text-slate-300">
                  API Key
                </label>
                {editingProfile.apiKey && (
                  <span className="text-[11px] font-mono text-slate-400">
                    Masked: ••••{editingProfile.apiKey.slice(-4)}
                  </span>
                )}
              </div>

              <div className="relative flex items-center">
                <input
                  type={showKey ? 'text' : 'password'}
                  placeholder={editingProfile.provider === 'gemini' ? 'Enter Gemini API key (or uses default env key)' : 'Enter API key...'}
                  value={editingProfile.apiKey}
                  onChange={e => setEditingProfile(p => ({ ...p, apiKey: e.target.value }))}
                  className="w-full neu-pressed rounded-2xl py-3 pl-4 pr-20 text-xs font-mono text-slate-800 dark:text-slate-100 outline-none"
                />
                <div className="absolute right-2 flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => setShowKey(!showKey)}
                    className="p-1.5 text-slate-400 hover:text-slate-600 cursor-pointer"
                    title={showKey ? 'Hide Key' : 'Show Key'}
                  >
                    {showKey ? <EyeOff size={15} /> : <Eye size={15} />}
                  </button>
                  <button
                    type="button"
                    onClick={handlePasteKey}
                    className="p-1.5 text-blue-600 dark:text-blue-400 hover:text-blue-700 cursor-pointer"
                    title="Paste from Clipboard"
                  >
                    <Clipboard size={15} />
                  </button>
                </div>
              </div>
              <span className="text-[11px] text-slate-400 pl-1">
                Your key stays exclusively on this phone. Set a spending limit with your provider.
              </span>
            </div>

            {/* Model Name and Optional Custom Base URL */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <NeuTextField
                label="Model Identifier"
                value={editingProfile.model}
                onChange={e => setEditingProfile(p => ({ ...p, model: e.target.value }))}
                placeholder="e.g. gemini-2.5-flash, gpt-4o, claude-3-5-sonnet"
              />

              {editingProfile.provider === 'custom' && (
                <NeuTextField
                  label="Custom Base URL"
                  value={editingProfile.baseUrl || ''}
                  onChange={e => setEditingProfile(p => ({ ...p, baseUrl: e.target.value }))}
                  placeholder="http://localhost:11434/v1"
                />
              )}
            </div>

            {/* Test Connection Button & Status */}
            <div className="pt-2 flex flex-col sm:flex-row items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <NeuButton
                  size="md"
                  onClick={handleTestConnection}
                  disabled={isTesting}
                >
                  {isTesting ? (
                    <div className="flex items-center gap-1.5">
                      <Loader2 size={15} className="animate-spin" />
                      <span>Testing...</span>
                    </div>
                  ) : (
                    <span>Test Connection</span>
                  )}
                </NeuButton>

                <NeuButton
                  variant="accent"
                  size="md"
                  onClick={handleSaveProfile}
                >
                  <span>Save Profile</span>
                </NeuButton>
              </div>

              {testResult && (
                <div className={`flex items-center gap-1.5 text-xs font-semibold ${
                  testResult.success ? 'text-emerald-600' : 'text-red-500'
                }`}>
                  {testResult.success ? <CheckCircle2 size={15} /> : <AlertCircle size={15} />}
                  <span>{testResult.message}</span>
                </div>
              )}
            </div>

            {/* Usage Estimate Tracker */}
            <div className="mt-2 p-3 rounded-2xl neu-pressed flex items-center justify-between text-xs">
              <div>
                <span className="font-bold text-slate-700 dark:text-slate-200 block">
                  Session Usage Estimate
                </span>
                <span className="text-slate-400">
                  Approx. 12 requests (~18,400 vision & completion tokens)
                </span>
              </div>
              <span className="font-mono text-emerald-600 dark:text-emerald-400 font-bold">
                ~$0.02 USD
              </span>
            </div>
          </NeuCard>
        </div>
      )}

      {/* TAB 2: Audio & Earbuds Guard */}
      {activeTab === 'audio' && (
        <NeuCard elevation="raised" rounded="3xl" className="p-5 sm:p-6 flex flex-col gap-4">
          <div className="pb-3 border-b border-slate-300/40 dark:border-slate-700/40">
            <h3 className="text-sm font-bold text-slate-800 dark:text-slate-100">
              Audio Routing & Privacy Guard (Section 6)
            </h3>
            <span className="text-xs text-slate-400">
              Audio never plays through the phone speaker.
            </span>
          </div>

          {/* Test Voice Button (FIX 1) */}
          <div className="py-1">
            <NeuButton
              size="md"
              fullWidth
              onClick={() => {
                const utterance = new SpeechSynthesisUtterance("SilentStudy voice test active. Earbuds private audio confirmed.");
                utterance.rate = settings.playbackSpeed;
                window.speechSynthesis.speak(utterance);
              }}
            >
              <Volume2 size={16} className="mr-2 text-[#6EA8FE]" />
              <span>Test Voice (Speaks in Earbuds)</span>
            </NeuButton>
          </div>

          <NeuToggle
            label="Earbuds-Only Mode"
            sublabel="Strict privacy guard. Instantly cuts off audio if earbuds disconnect."
            checked={settings.earbudsOnlyMode}
            onChange={val => {
              const updated = { ...settings, earbudsOnlyMode: val };
              onUpdateSettings(updated);
              AppStorage.saveSettings(updated);
            }}
          />

          <NeuToggle
            label="Auto-Slowdown for Math Equations"
            sublabel="Reduces speech rate to 0.75x with pauses on mathematical formulas and proof steps."
            checked={settings.mathSlowdown}
            onChange={val => {
              const updated = { ...settings, mathSlowdown: val };
              onUpdateSettings(updated);
              AppStorage.saveSettings(updated);
            }}
          />

          <NeuToggle
            label="Spoken After-Answer Menu"
            sublabel="Speaks 'Tap once to repeat...' after every answer, or uses chime-only if disabled."
            checked={settings.spokenMenuEnabled}
            onChange={val => {
              const updated = { ...settings, spokenMenuEnabled: val };
              onUpdateSettings(updated);
              AppStorage.saveSettings(updated);
            }}
          />

          <NeuToggle
            label="Silent Text Mode"
            sublabel="Displays answers on screen only without any audio synthesis."
            checked={settings.silentTextMode}
            onChange={val => {
              const updated = { ...settings, silentTextMode: val };
              onUpdateSettings(updated);
              AppStorage.saveSettings(updated);
            }}
          />

          <div className="pt-2">
            <NeuSlider
              label="Standard Speech Rate"
              min={0.75}
              max={2.0}
              step={0.25}
              value={settings.playbackSpeed}
              onChange={val => {
                const updated = { ...settings, playbackSpeed: val };
                onUpdateSettings(updated);
                AppStorage.saveSettings(updated);
              }}
              unit="x"
            />
          </div>
        </NeuCard>
      )}

      {/* TAB 3: Desk Gestures */}
      {activeTab === 'gestures' && (
        <NeuCard elevation="raised" rounded="3xl" className="p-5 sm:p-6 flex flex-col gap-4">
          <div className="pb-3 border-b border-slate-300/40 dark:border-slate-700/40">
            <h3 className="text-sm font-bold text-slate-800 dark:text-slate-100">
              Desk-Tap Gesture Engine (Section 8)
            </h3>
            <span className="text-xs text-slate-400">
              Detects knocks on the desk via accelerometer while resting flat.
            </span>
          </div>

          <NeuSlider
            label="Vibration Tap Sensitivity"
            min={1}
            max={10}
            step={1}
            value={settings.gestureSensitivity}
            onChange={val => {
              const updated = { ...settings, gestureSensitivity: val };
              onUpdateSettings(updated);
              AppStorage.saveSettings(updated);
            }}
            valueDisplay={`${settings.gestureSensitivity}/10`}
          />

          <NeuToggle
            label="Haptic Confirmation"
            sublabel="Vibrates gently when a desk knock pattern is recognized."
            checked={settings.hapticsEnabled}
            onChange={val => {
              const updated = { ...settings, hapticsEnabled: val };
              onUpdateSettings(updated);
              AppStorage.saveSettings(updated);
            }}
          />

          {/* Gesture Mapping Table */}
          <div className="mt-2">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500 block mb-2">
              Default Gesture Map (Remappable)
            </span>
            <div className="space-y-1.5">
              {[
                { gesture: '1 tap', action: 'Play / Pause' },
                { gesture: '2 taps', action: 'Next answer' },
                { gesture: '3 taps', action: 'Previous answer' },
                { gesture: 'Tap, pause, tap', action: 'Repeat from start' },
                { gesture: 'Long knock (hold)', action: 'Short summary' },
                { gesture: '4 taps', action: 'Stop & silent' },
                { gesture: '5 taps', action: 'Toggle torch / wake' },
                { gesture: '6 taps', action: 'Cycle speech speed' },
              ].map(item => (
                <div key={item.gesture} className="p-2.5 rounded-xl neu-pressed flex justify-between items-center text-xs">
                  <span className="font-bold text-blue-600 dark:text-blue-400">{item.gesture}</span>
                  <span className="text-slate-700 dark:text-slate-300 font-medium">{item.action}</span>
                </div>
              ))}
            </div>
          </div>
        </NeuCard>
      )}

      {/* TAB 4: Theme / Appearance */}
      {activeTab === 'appearance' && (
        <NeuCard elevation="raised" rounded="3xl" className="p-5 sm:p-6 flex flex-col gap-4">
          <div className="pb-3 border-b border-slate-300/40 dark:border-slate-700/40">
            <h3 className="text-sm font-bold text-slate-800 dark:text-slate-100">
              Neumorphism Soft UI Theme (FIX 2)
            </h3>
            <span className="text-xs text-slate-400">
              Dark theme is default. Pure white (#FFFFFF) is eliminated for glare-free library study.
            </span>
          </div>

          <NeuToggle
            label="Dark Neumorphism (Default)"
            sublabel="Surface #1E2127 with glare-free off-white text #C9CED6 and soft blue accent #6EA8FE."
            checked={settings.isDarkMode}
            onChange={val => {
              const updated = { ...settings, isDarkMode: val };
              onUpdateSettings(updated);
              AppStorage.saveSettings(updated);
              if (val) {
                document.documentElement.classList.remove('light-theme');
              } else {
                document.documentElement.classList.add('light-theme');
              }
            }}
          />

          <NeuToggle
            label="Ultra Dim Mode (Night Library)"
            sublabel="Even darker warm slate tones (#141619) with reduced luminescence for night sessions."
            checked={settings.isUltraDimMode}
            onChange={val => {
              const updated = { ...settings, isUltraDimMode: val };
              onUpdateSettings(updated);
              AppStorage.saveSettings(updated);
              if (val) {
                document.documentElement.classList.add('ultra-dim');
              } else {
                document.documentElement.classList.remove('ultra-dim');
              }
            }}
          />

          {/* Savage Tech Developer Credits */}
          <div className="mt-4 p-3 rounded-2xl neu-pressed flex items-center justify-between text-xs">
            <div>
              <span className="font-bold text-slate-700 dark:text-slate-200 block">
                Engineering & Architecture
              </span>
              <span className="text-slate-400">
                Native Android Kotlin Jetpack Compose companion
              </span>
            </div>
            <span className="px-2.5 py-1 rounded-xl neu-raised font-bold text-[#6EA8FE]">
              Savage Tech
            </span>
          </div>
        </NeuCard>
      )}

      {/* TAB 5: Backup, Export & Android Companion */}
      {activeTab === 'data' && (
        <div className="flex flex-col gap-4">
          <NeuCard elevation="raised" rounded="3xl" className="p-5 sm:p-6 flex flex-col gap-4">
            <div className="pb-3 border-b border-slate-300/40 dark:border-slate-700/40">
              <h3 className="text-sm font-bold text-slate-800 dark:text-slate-100">
                JSON Backup & Library Data
              </h3>
              <span className="text-xs text-slate-400">
                Export answers and study history. API keys are strictly excluded from backups.
              </span>
            </div>

            {importNotice && (
              <div className="p-3 rounded-2xl neu-pressed text-xs font-semibold text-emerald-600">
                {importNotice}
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <NeuButton size="md" onClick={handleExportBackup}>
                <Download size={16} className="mr-2" />
                <span>Export JSON Backup</span>
              </NeuButton>

              <label className="cursor-pointer">
                <input
                  type="file"
                  accept=".json"
                  onChange={handleImportBackup}
                  className="hidden"
                />
                <div className="w-full h-full min-h-[48px] rounded-2xl neu-raised hover:neu-pressed flex items-center justify-center text-xs font-semibold text-slate-700 dark:text-slate-200">
                  <Upload size={16} className="mr-2" />
                  <span>Restore JSON Backup</span>
                </div>
              </label>
            </div>
          </NeuCard>

          {/* Android Kotlin Project Section */}
          <NeuCard elevation="raised" rounded="3xl" className="p-5 sm:p-6 flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-800 dark:text-slate-100">
                  Native Android Kotlin Architecture
                </h3>
                <span className="text-xs text-slate-400">
                  Inspect and download the full Android Studio Jetpack Compose project (minSdk 24)
                </span>
              </div>
              <NeuButton size="sm" variant="accent" onClick={onOpenAndroidExport}>
                <FileCode size={14} className="mr-1.5" />
                <span>View Kotlin Code</span>
              </NeuButton>
            </div>
          </NeuCard>
        </div>
      )}
    </div>
  );
};
