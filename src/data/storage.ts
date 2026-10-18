import { AnswerItem, AppSettings, AiProfile } from '../types';
import { SAMPLE_ANSWERS } from './sampleData';

const STORAGE_KEYS = {
  ANSWERS: 'silentstudy_answers_v1',
  NEXT_ID: 'silentstudy_next_id_v1',
  SETTINGS: 'silentstudy_settings_v1',
  PROFILES: 'silentstudy_ai_profiles_v1',
  SESSIONS: 'silentstudy_sessions_v1',
};

const DEFAULT_PROFILES: AiProfile[] = [
  {
    id: 'profile-gemini-default',
    name: 'Google Gemini (Flash 2.5)',
    provider: 'gemini',
    apiKey: '', // Will use VITE_GEMINI_API_KEY from environment if blank
    model: 'gemini-2.5-flash',
    temperature: 0.2,
    maxTokens: 3000,
    isDefault: true
  },
  {
    id: 'profile-openai-mini',
    name: 'OpenAI (GPT-4o mini)',
    provider: 'openai',
    apiKey: '',
    model: 'gpt-4o-mini',
    temperature: 0.2,
    maxTokens: 3000
  },
  {
    id: 'profile-claude-haiku',
    name: 'Anthropic Claude (3.5 Haiku)',
    provider: 'claude',
    apiKey: '',
    model: 'claude-3-5-haiku-20241022',
    temperature: 0.2,
    maxTokens: 3000
  },
  {
    id: 'profile-openrouter-universal',
    name: 'OpenRouter (Multi-model)',
    provider: 'openrouter',
    apiKey: '',
    model: 'google/gemini-2.0-flash-001',
    temperature: 0.2,
    maxTokens: 3000
  },
  {
    id: 'profile-local-ollama',
    name: 'Local Ollama / LM Studio',
    provider: 'custom',
    apiKey: '',
    baseUrl: 'http://localhost:11434/v1',
    model: 'llama3.2-vision',
    temperature: 0.2,
    maxTokens: 3000
  }
];

export const DEFAULT_SETTINGS: AppSettings = {
  isDarkMode: true, // Dark neumorphism DEFAULT (FIX 2)
  isUltraDimMode: false,
  accentColor: '#6EA8FE',
  textSize: 'medium',

  earbudsOnlyMode: true,
  playbackSpeed: 1.0,
  mathSlowdown: true,
  mathSlowdownRate: 0.75,
  defaultDetailMode: 'detailed',
  spokenMenuEnabled: true,
  silentTextMode: false,
  ttsPitch: 1.0,

  gestureSensitivity: 6,
  tapWindowMs: 600,
  hapticsEnabled: true,
  torchGestureEnabled: true,
  gestureMappings: {
    '1_tap': 'play_pause',
    '2_taps': 'next_answer',
    '3_taps': 'prev_answer',
    'tap_pause_tap': 'repeat',
    'long_knock': 'short_summary',
    'long_knock_tap': 'jump_to_n',
    '4_taps': 'stop_silent',
    '5_taps': 'toggle_torch',
    '6_taps': 'cycle_speed',
    'knock_knock_long': 'switch_mode'
  },

  voiceCommandsEnabled: false,
  wakeWordEnabled: false,
  confirmLowConfidence: true,

  autoEnhance: true,
  multiPhotoEnabled: true,
  timerLengthSec: 3,
  volumeShutterEnabled: true,

  activeProfileId: 'profile-gemini-default',
  profiles: DEFAULT_PROFILES,
  perTaskProvider: false,

  quizModeEnabled: true,
  spacedRepetitionEnabled: true,
  stepByStepSolverEnabled: true,
  followUpQuestionsEnabled: true,
  pomodoroMinutes: 25,
  pomodoroBreakMinutes: 5,
  batterySaverEnabled: true,
  autoStopMinutes: 15
};

export class AppStorage {
  /**
   * Load all answers from persistent storage
   */
  static getAnswers(): AnswerItem[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.ANSWERS);
      if (!raw) {
        // Initialize with rich sample answers
        this.saveAnswers(SAMPLE_ANSWERS);
        this.setNextId(SAMPLE_ANSWERS.length + 1);
        return SAMPLE_ANSWERS;
      }
      return JSON.parse(raw);
    } catch {
      return SAMPLE_ANSWERS;
    }
  }

  static saveAnswers(answers: AnswerItem[]): void {
    localStorage.setItem(STORAGE_KEYS.ANSWERS, JSON.stringify(answers));
  }

  static getNextId(): number {
    const raw = localStorage.getItem(STORAGE_KEYS.NEXT_ID);
    if (!raw) {
      const answers = this.getAnswers();
      const maxId = answers.reduce((max, a) => Math.max(max, a.id), 0);
      return maxId + 1;
    }
    return parseInt(raw, 10) || 1;
  }

  static setNextId(id: number): void {
    localStorage.setItem(STORAGE_KEYS.NEXT_ID, id.toString());
  }

  /**
   * Creates a new answer with a permanent, monotonic auto-increment ID
   */
  static addAnswer(data: Omit<AnswerItem, 'id' | 'createdAt'>): AnswerItem {
    const nextId = this.getNextId();
    const newAnswer: AnswerItem = {
      ...data,
      id: nextId,
      createdAt: Date.now()
    };
    
    // Save updated ID counter so numbers are NEVER reused even after deletion
    this.setNextId(nextId + 1);

    const answers = this.getAnswers();
    answers.unshift(newAnswer);
    this.saveAnswers(answers);
    return newAnswer;
  }

  static updateAnswer(updated: AnswerItem): void {
    const answers = this.getAnswers();
    const idx = answers.findIndex(a => a.id === updated.id);
    if (idx !== -1) {
      answers[idx] = updated;
      this.saveAnswers(answers);
    }
  }

  static toggleStar(id: number): boolean {
    const answers = this.getAnswers();
    const item = answers.find(a => a.id === id);
    if (item) {
      item.starred = !item.starred;
      this.saveAnswers(answers);
      return !!item.starred;
    }
    return false;
  }

  static deleteAnswer(id: number): AnswerItem | null {
    const answers = this.getAnswers();
    const target = answers.find(a => a.id === id);
    if (target) {
      const filtered = answers.filter(a => a.id !== id);
      this.saveAnswers(filtered);
      return target;
    }
    return null;
  }

  static restoreAnswer(answer: AnswerItem): void {
    const answers = this.getAnswers();
    // Insert back in appropriate sorted place or beginning
    answers.unshift(answer);
    this.saveAnswers(answers);
  }

  /**
   * Settings Management
   */
  static getSettings(): AppSettings {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.SETTINGS);
      if (!raw) return DEFAULT_SETTINGS;
      const parsed = JSON.parse(raw);
      return { ...DEFAULT_SETTINGS, ...parsed };
    } catch {
      return DEFAULT_SETTINGS;
    }
  }

  static saveSettings(settings: AppSettings): void {
    localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(settings));
  }

  /**
   * AI Profiles
   */
  static getProfiles(): AiProfile[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.PROFILES);
      if (!raw) return DEFAULT_PROFILES;
      return JSON.parse(raw);
    } catch {
      return DEFAULT_PROFILES;
    }
  }

  static saveProfiles(profiles: AiProfile[]): void {
    localStorage.setItem(STORAGE_KEYS.PROFILES, JSON.stringify(profiles));
  }

  static saveProfile(profile: AiProfile): void {
    const profiles = this.getProfiles();
    const idx = profiles.findIndex(p => p.id === profile.id);
    if (idx !== -1) {
      profiles[idx] = profile;
    } else {
      profiles.push(profile);
    }
    this.saveProfiles(profiles);
  }

  static deleteProfile(id: string): void {
    const profiles = this.getProfiles().filter(p => p.id !== id);
    this.saveProfiles(profiles);
  }

  /**
   * Backup & Export
   * Strictly strips API keys before exporting!
   */
  static exportBackupJson(): string {
    const answers = this.getAnswers();
    const settings = { ...this.getSettings() };
    const profiles = this.getProfiles().map(p => ({
      ...p,
      apiKey: '' // Never include keys in backup
    }));

    const backupData = {
      version: '1.0',
      exportedAt: new Date().toISOString(),
      appName: 'SilentStudy',
      answers,
      settings: {
        ...settings,
        profiles
      }
    };

    return JSON.stringify(backupData, null, 2);
  }

  static importBackupJson(jsonString: string): { success: boolean; count: number; message: string } {
    try {
      const parsed = JSON.parse(jsonString);
      if (!parsed.answers || !Array.isArray(parsed.answers)) {
        return { success: false, count: 0, message: 'Invalid backup file format.' };
      }

      this.saveAnswers(parsed.answers);
      const maxId = parsed.answers.reduce((max: number, a: AnswerItem) => Math.max(max, a.id), 0);
      this.setNextId(maxId + 1);

      return {
        success: true,
        count: parsed.answers.length,
        message: `Successfully restored ${parsed.answers.length} study answers.`
      };
    } catch (e: any) {
      return { success: false, count: 0, message: e.message || 'Failed to parse JSON backup.' };
    }
  }
}
