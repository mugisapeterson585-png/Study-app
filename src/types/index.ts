export type Subject = 
  | 'Mathematics' 
  | 'Physics' 
  | 'Chemistry' 
  | 'Biology' 
  | 'History' 
  | 'Computer Science' 
  | 'Literature' 
  | 'Economics' 
  | 'General';

export type BlockType = 'text' | 'math' | 'step';

export interface AnswerBlock {
  type: BlockType;
  display: string;   // Written form (KaTeX LaTeX, numbered step, or clean text)
  spoken: string;    // Natural language reading optimized for earbud TTS
  stepNumber?: number;
}

export interface AnswerItem {
  id: number;                   // Permanent auto-incrementing number (#1, #2, #3...)
  subject: Subject | string;
  questionText: string;
  imagePaths: string[];         // Base64 or local blob URLs of captured photos
  shortAnswer: string;          // 1-2 sentence TL;DR
  blocks: AnswerBlock[];
  simpleExplanation: string;    // Explain like I'm new
  starred?: boolean;
  createdAt: number;            // Timestamp
  lastPlayedAt?: number;
  quizStats?: {
    timesReviewed: number;
    timesCorrect: number;
    lastReviewedAt: number;
    intervalDays: number;
    easeFactor: number;
  };
}

export type AiProviderType = 'gemini' | 'claude' | 'openai' | 'openrouter' | 'custom';

export interface AiProfile {
  id: string;
  name: string;
  provider: AiProviderType;
  apiKey: string;
  model: string;
  baseUrl?: string;
  temperature: number;
  maxTokens: number;
  isDefault?: boolean;
  isFallback?: boolean;
}

export type GestureType = 
  | '1_tap'
  | '2_taps'
  | '3_taps'
  | 'tap_pause_tap'
  | 'long_knock'
  | 'long_knock_tap'
  | '4_taps'
  | '5_taps'
  | '6_taps'
  | 'knock_knock_long';

export type GestureAction = 
  | 'play_pause'
  | 'next_answer'
  | 'prev_answer'
  | 'repeat'
  | 'short_summary'
  | 'jump_to_n'
  | 'stop_silent'
  | 'toggle_torch'
  | 'cycle_speed'
  | 'switch_mode'
  | 'explain_simpler';

export interface GestureMapping {
  gesture: GestureType;
  label: string;
  action: GestureAction;
  description: string;
}

export type PlaybackDetailMode = 'short' | 'detailed' | 'simple';

export interface AppSettings {
  // Theme
  isDarkMode: boolean;            // Dark by default (FIX 2)
  isUltraDimMode: boolean;        // Ultra dim warm night mode (FIX 2)
  accentColor: string;
  textSize: 'small' | 'medium' | 'large';

  // Audio & Privacy
  earbudsOnlyMode: boolean;       // MUST be earbuds only (stops if phone speaker)
  playbackSpeed: number;          // 0.75, 1.0, 1.25, 1.5, 2.0
  mathSlowdown: boolean;          // Auto-slowdown on math/step blocks
  mathSlowdownRate: number;       // 0.5 - 0.9x (default 0.75x)
  defaultDetailMode: PlaybackDetailMode;
  spokenMenuEnabled: boolean;     // Spoken after-answer menu vs chime-only
  silentTextMode: boolean;        // Show on screen only
  ttsVoiceUri?: string;
  ttsPitch: number;

  // Gestures
  gestureSensitivity: number;     // 1 to 10
  tapWindowMs: number;            // default 600ms
  hapticsEnabled: boolean;
  torchGestureEnabled: boolean;
  gestureMappings: Record<GestureType, GestureAction>;

  // Voice
  voiceCommandsEnabled: boolean;
  wakeWordEnabled: boolean;
  confirmLowConfidence: boolean;

  // Camera & Capture
  autoEnhance: boolean;
  multiPhotoEnabled: boolean;
  timerLengthSec: number;         // default 3
  volumeShutterEnabled: boolean;

  // AI & Providers
  activeProfileId: string;
  fallbackProfileId?: string;
  profiles: AiProfile[];
  perTaskProvider: boolean;

  // Extras
  quizModeEnabled: boolean;
  spacedRepetitionEnabled: boolean;
  stepByStepSolverEnabled: boolean;
  followUpQuestionsEnabled: boolean;
  pomodoroMinutes: number;        // 25
  pomodoroBreakMinutes: number;   // 5
  batterySaverEnabled: boolean;
  autoStopMinutes: number;        // 15
}

export interface StudySessionState {
  isActive: boolean;
  startTime: number | null;
  durationSeconds: number;
  answersHeard: number;
  pomodoroStage: 'work' | 'break' | 'idle';
  pomodoroTimeRemaining: number;
}
