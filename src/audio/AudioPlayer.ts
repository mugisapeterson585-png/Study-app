import { AnswerBlock, AnswerItem, AppSettings, PlaybackDetailMode } from '../types';
import { EarbudsGuard } from './EarbudsAudioGuard';

export type PlayerState = 'idle' | 'playing' | 'paused' | 'after_answer_menu' | 'blocked_no_earbuds';

export interface PlayerProgress {
  currentAnswerId: number | null;
  state: PlayerState;
  currentBlockIndex: number;
  totalBlocks: number;
  activeBlock: AnswerBlock | null;
  isMathSlowdownActive: boolean;
  menuCountdownRemaining: number;
}

type PlayerListener = (progress: PlayerProgress) => void;

class AudioPlayerService {
  private audioCtx: AudioContext | null = null;
  private currentUtterance: SpeechSynthesisUtterance | null = null;
  private currentAnswer: AnswerItem | null = null;
  private currentBlocks: AnswerBlock[] = [];
  private currentBlockIndex: number = 0;
  private state: PlayerState = 'idle';
  private detailMode: PlaybackDetailMode = 'detailed';
  private settings: AppSettings | null = null;
  private listeners: Set<PlayerListener> = new Set();
  private isMathSlowActive: boolean = false;
  private menuTimer: any = null;
  private menuSecondsRemaining: number = 0;
  private onAfterMenuAction?: (action: 'repeat' | 'next' | 'simple') => void;

  constructor() {
    // Listen to earbud disconnects
    EarbudsGuard.subscribe((status) => {
      if (!status.hasEarbudsConnected && (this.state === 'playing' || this.state === 'after_answer_menu')) {
        this.stopImmediate('Privacy Guard: Earbuds disconnected. Playback halted instantly.');
      }
    });
  }

  public setSettings(settings: AppSettings) {
    this.settings = settings;
    this.detailMode = settings.defaultDetailMode;
  }

  public setDetailMode(mode: PlaybackDetailMode) {
    this.detailMode = mode;
  }

  public setAfterMenuCallback(callback: (action: 'repeat' | 'next' | 'simple') => void) {
    this.onAfterMenuAction = callback;
  }

  private getAudioContext(): AudioContext {
    if (!this.audioCtx) {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      this.audioCtx = new AudioCtx();
    }
    if (this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
    return this.audioCtx;
  }

  /**
   * Play soft earbud tick chime for desk gestures (e.g., 1 tick = 1 tap recognized)
   */
  public playGestureTick(count: number = 1) {
    if (this.settings?.earbudsOnlyMode && !EarbudsGuard.getStatus().hasEarbudsConnected) {
      return;
    }
    try {
      const ctx = this.getAudioContext();
      for (let i = 0; i < count; i++) {
        const time = ctx.currentTime + i * 0.12;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(880, time);
        osc.frequency.exponentialRampToValueAtTime(440, time + 0.05);

        gain.gain.setValueAtTime(0.08, time);
        gain.gain.exponentialRampToValueAtTime(0.001, time + 0.05);

        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(time);
        osc.stop(time + 0.06);
      }
    } catch (e) {
      console.warn('Audio tick error:', e);
    }
  }

  /**
   * Play completion chime
   */
  public playChime() {
    if (this.settings?.earbudsOnlyMode && !EarbudsGuard.getStatus().hasEarbudsConnected) return;
    try {
      const ctx = this.getAudioContext();
      const now = ctx.currentTime;
      const notes = [523.25, 659.25, 783.99]; // C5, E5, G5
      notes.forEach((freq, idx) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        const start = now + idx * 0.1;
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(freq, start);
        gain.gain.setValueAtTime(0.09, start);
        gain.gain.exponentialRampToValueAtTime(0.001, start + 0.35);

        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(start);
        osc.stop(start + 0.4);
      });
    } catch (e) {
      console.warn('Chime error:', e);
    }
  }

  /**
   * Start playback of an answer item
   */
  public playAnswer(answer: AnswerItem, mode?: PlaybackDetailMode) {
    this.clearMenuTimer();

    // Check Earbuds-only privacy guard
    if (this.settings?.earbudsOnlyMode && !EarbudsGuard.getStatus().hasEarbudsConnected) {
      this.state = 'blocked_no_earbuds';
      this.notify();
      return;
    }

    if (this.settings?.silentTextMode) {
      // Silent text mode: user reads on screen only
      this.currentAnswer = answer;
      this.state = 'playing';
      this.notify();
      return;
    }

    this.stopSpeech();
    this.currentAnswer = answer;
    if (mode) this.detailMode = mode;

    // Prepare blocks according to mode
    if (this.detailMode === 'short') {
      this.currentBlocks = [
        {
          type: 'text',
          display: answer.shortAnswer,
          spoken: answer.shortAnswer
        }
      ];
    } else if (this.detailMode === 'simple') {
      this.currentBlocks = [
        {
          type: 'text',
          display: answer.simpleExplanation,
          spoken: answer.simpleExplanation
        }
      ];
    } else {
      // Detailed: full blocks
      this.currentBlocks = answer.blocks && answer.blocks.length > 0
        ? answer.blocks
        : [{ type: 'text', display: answer.shortAnswer, spoken: answer.shortAnswer }];
    }

    this.currentBlockIndex = 0;
    this.state = 'playing';
    this.playCurrentBlock();
  }

  private playCurrentBlock() {
    if (!('speechSynthesis' in window)) {
      console.error('SpeechSynthesis not supported');
      return;
    }

    if (this.state !== 'playing' || !this.currentBlocks || this.currentBlockIndex >= this.currentBlocks.length) {
      this.onAnswerFinished();
      return;
    }

    // Check earbuds guard again
    if (this.settings?.earbudsOnlyMode && !EarbudsGuard.getStatus().hasEarbudsConnected) {
      this.stopImmediate('Privacy Guard: No earbuds connected.');
      return;
    }

    const block = this.currentBlocks[this.currentBlockIndex];
    let spokenText = block.spoken || block.display;

    // Prepend Step N announcement if step block
    if (block.type === 'step' && block.stepNumber) {
      spokenText = `Step ${block.stepNumber}. ${spokenText}`;
    }

    const utterance = new SpeechSynthesisUtterance(spokenText);
    this.currentUtterance = utterance;

    // Determine speed
    const baseRate = this.settings?.playbackSpeed || 1.0;
    const isMathOrStep = block.type === 'math' || block.type === 'step';

    if (this.settings?.mathSlowdown && isMathOrStep) {
      const slowRate = this.settings.mathSlowdownRate || 0.75;
      utterance.rate = Math.max(0.5, Math.min(2.0, baseRate * slowRate));
      this.isMathSlowActive = true;
    } else {
      utterance.rate = baseRate;
      this.isMathSlowActive = false;
    }

    utterance.pitch = this.settings?.ttsPitch || 1.0;

    // Voice selection
    if (this.settings?.ttsVoiceUri) {
      const voices = window.speechSynthesis.getVoices();
      const voice = voices.find(v => v.voiceURI === this.settings?.ttsVoiceUri);
      if (voice) utterance.voice = voice;
    }

    utterance.onend = () => {
      // Pause handling
      const pauseDuration = isMathOrStep ? 500 : 250;
      setTimeout(() => {
        if (this.state === 'playing') {
          this.currentBlockIndex++;
          this.playCurrentBlock();
        }
      }, pauseDuration);
    };

    utterance.onerror = (e) => {
      console.warn('TTS utterance error:', e);
      if (this.state === 'playing') {
        this.currentBlockIndex++;
        this.playCurrentBlock();
      }
    };

    this.notify();
    window.speechSynthesis.speak(utterance);
  }

  private onAnswerFinished() {
    this.playChime();
    this.state = 'after_answer_menu';
    this.menuSecondsRemaining = 5;
    this.notify();

    if (this.settings?.spokenMenuEnabled !== false) {
      // Announce menu instructions in earbuds
      const menuUtterance = new SpeechSynthesisUtterance("Tap once to repeat. Tap twice for next answer.");
      menuUtterance.rate = this.settings?.playbackSpeed || 1.0;
      window.speechSynthesis.speak(menuUtterance);
    }

    // 5-second countdown timer for gesture input
    this.clearMenuTimer();
    this.menuTimer = setInterval(() => {
      this.menuSecondsRemaining -= 1;
      if (this.menuSecondsRemaining <= 0) {
        this.clearMenuTimer();
        this.state = 'idle';
        this.notify();
      } else {
        this.notify();
      }
    }, 1000);
  }

  /**
   * Handle desk gestures specifically during after-answer menu
   */
  public handleAfterAnswerGesture(tapCount: number): boolean {
    if (this.state !== 'after_answer_menu') return false;

    this.clearMenuTimer();
    this.stopSpeech();

    if (tapCount === 1) {
      // 1 tap = repeat
      if (this.currentAnswer) {
        this.playAnswer(this.currentAnswer);
        this.onAfterMenuAction?.('repeat');
        return true;
      }
    } else if (tapCount === 2) {
      // 2 taps = next
      this.state = 'idle';
      this.notify();
      this.onAfterMenuAction?.('next');
      return true;
    } else if (tapCount === 3) {
      // 3 taps = explain simpler
      if (this.currentAnswer) {
        this.playAnswer(this.currentAnswer, 'simple');
        this.onAfterMenuAction?.('simple');
        return true;
      }
    }

    return false;
  }

  public togglePlayPause() {
    if (this.state === 'playing') {
      if (window.speechSynthesis) {
        window.speechSynthesis.pause();
      }
      this.state = 'paused';
      this.notify();
    } else if (this.state === 'paused') {
      if (window.speechSynthesis) {
        window.speechSynthesis.resume();
      }
      this.state = 'playing';
      this.notify();
    } else if (this.state === 'idle' && this.currentAnswer) {
      this.playAnswer(this.currentAnswer);
    }
  }

  public pause() {
    if (this.state === 'playing') {
      if (window.speechSynthesis) window.speechSynthesis.pause();
      this.state = 'paused';
      this.notify();
    }
  }

  public resume() {
    if (this.state === 'paused') {
      if (window.speechSynthesis) window.speechSynthesis.resume();
      this.state = 'playing';
      this.notify();
    }
  }

  public stopImmediate(reason?: string) {
    this.clearMenuTimer();
    this.stopSpeech();
    this.state = 'idle';
    if (reason) console.info(reason);
    this.notify();
  }

  private stopSpeech() {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
  }

  private clearMenuTimer() {
    if (this.menuTimer) {
      clearInterval(this.menuTimer);
      this.menuTimer = null;
    }
  }

  public subscribe(listener: PlayerListener): () => void {
    this.listeners.add(listener);
    this.notify();
    return () => this.listeners.delete(listener);
  }

  private notify() {
    const progress: PlayerProgress = {
      currentAnswerId: this.currentAnswer ? this.currentAnswer.id : null,
      state: this.state,
      currentBlockIndex: this.currentBlockIndex,
      totalBlocks: this.currentBlocks.length,
      activeBlock: this.currentBlocks[this.currentBlockIndex] || null,
      isMathSlowdownActive: this.isMathSlowActive,
      menuCountdownRemaining: this.menuSecondsRemaining,
    };
    this.listeners.forEach(l => l(progress));
  }

  public getCurrentAnswer(): AnswerItem | null {
    return this.currentAnswer;
  }
}

export const AudioPlayer = new AudioPlayerService();
