import { GestureAction, GestureType } from '../types';
import { AudioPlayer } from '../audio/AudioPlayer';

export interface TapSpikeData {
  time: number;
  magnitude: number;
  threshold: number;
  isPeak: boolean;
}

export type GestureDetectedListener = (gesture: GestureType, rawTaps: number) => void;
export type WaveformListener = (spike: TapSpikeData) => void;

class DeskTapGestureEngine {
  private isListening: boolean = false;
  private sensitivity: number = 5; // 1 (least sensitive) to 10 (most sensitive)
  private tapWindowMs: number = 600; // grouping window
  private threshold: number = 1.8; // m/s^2 above resting gravity
  private lastAcc = { x: 0, y: 0, z: 9.8 };
  private gravity = { x: 0, y: 0, z: 9.8 };
  private tapTimestamps: number[] = [];
  private groupTimeout: any = null;
  private longKnockStart: number | null = null;
  private isLongKnockDetected: boolean = false;

  private gestureListeners: Set<GestureDetectedListener> = new Set();
  private waveformListeners: Set<WaveformListener> = new Set();

  constructor() {
    this.updateThreshold();
  }

  public setSensitivity(level: number) {
    this.sensitivity = Math.max(1, Math.min(10, level));
    this.updateThreshold();
  }

  public setTapWindow(windowMs: number) {
    this.tapWindowMs = windowMs;
  }

  private updateThreshold() {
    // 1 -> 3.5 m/s^2 (hard knocks only)
    // 10 -> 0.6 m/s^2 (light finger taps)
    this.threshold = 3.5 - (this.sensitivity - 1) * 0.32;
  }

  public startListening(): boolean {
    if (this.isListening) return true;

    if (typeof window !== 'undefined' && 'DeviceMotionEvent' in window) {
      // Check if permission required on iOS 13+
      if (typeof (DeviceMotionEvent as any).requestPermission === 'function') {
        (DeviceMotionEvent as any).requestPermission().then((permissionState: string) => {
          if (permissionState === 'granted') {
            window.addEventListener('devicemotion', this.handleMotion, false);
            this.isListening = true;
          }
        }).catch(() => {
          this.isListening = true; // Fallback to simulated taps
        });
      } else {
        window.addEventListener('devicemotion', this.handleMotion, false);
        this.isListening = true;
      }
    } else {
      this.isListening = true;
    }

    return this.isListening;
  }

  public stopListening() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('devicemotion', this.handleMotion);
    }
    this.isListening = false;
    this.clearGroupTimer();
  }

  public getIsListening(): boolean {
    return this.isListening;
  }

  private handleMotion = (event: DeviceMotionEvent) => {
    const acc = event.accelerationIncludingGravity;
    if (!acc || acc.x === null || acc.y === null || acc.z === null) return;

    // High-pass filter to isolate sudden transient spikes from slow tilts
    const alpha = 0.8;
    this.gravity.x = alpha * this.gravity.x + (1 - alpha) * acc.x;
    this.gravity.y = alpha * this.gravity.y + (1 - alpha) * acc.y;
    this.gravity.z = alpha * this.gravity.z + (1 - alpha) * acc.z;

    const linearX = acc.x - this.gravity.x;
    const linearY = acc.y - this.gravity.y;
    const linearZ = acc.z - this.gravity.z;

    const magnitude = Math.sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ);
    const now = performance.now();

    // Broadcast waveform for live visualizer
    if (this.waveformListeners.size > 0) {
      this.waveformListeners.forEach(listener => listener({
        time: now,
        magnitude,
        threshold: this.threshold,
        isPeak: magnitude > this.threshold
      }));
    }

    // Check for sustained vibration (long knock ~800ms)
    if (magnitude > this.threshold * 0.8) {
      if (!this.longKnockStart) {
        this.longKnockStart = now;
      } else if (now - this.longKnockStart > 750 && !this.isLongKnockDetected) {
        this.isLongKnockDetected = true;
        this.triggerGesture('long_knock', 1);
      }
    } else {
      if (this.longKnockStart && now - this.longKnockStart < 750) {
        // Was a short tap spike
        if (magnitude > this.threshold) {
          this.recordTap(now);
        }
      }
      this.longKnockStart = null;
      this.isLongKnockDetected = false;
    }
  };

  /**
   * Simulates a desk knock manually from UI or keyboard
   */
  public triggerSimulatedKnock(type?: GestureType) {
    if (type) {
      this.triggerGesture(type, 1);
      return;
    }
    const now = performance.now();
    // Broadcast waveform peak
    if (this.waveformListeners.size > 0) {
      this.waveformListeners.forEach(listener => listener({
        time: now,
        magnitude: this.threshold * 1.5,
        threshold: this.threshold,
        isPeak: true
      }));
    }
    this.recordTap(now);
  }

  public triggerSimulatedLongKnock() {
    this.triggerGesture('long_knock', 1);
  }

  private recordTap(timestamp: number) {
    const lastTap = this.tapTimestamps[this.tapTimestamps.length - 1];
    // Reject debounce under 70ms
    if (lastTap && timestamp - lastTap < 70) return;

    this.tapTimestamps.push(timestamp);
    AudioPlayer.playGestureTick(1); // Feedback tick

    this.clearGroupTimer();
    this.groupTimeout = setTimeout(() => {
      this.evaluatePattern();
    }, this.tapWindowMs);
  }

  private evaluatePattern() {
    const count = this.tapTimestamps.length;
    if (count === 0) return;

    // Check for "tap, pause, tap"
    if (count === 2) {
      const delta = this.tapTimestamps[1] - this.tapTimestamps[0];
      if (delta > 350) {
        this.triggerGesture('tap_pause_tap', count);
        this.tapTimestamps = [];
        return;
      }
    }

    if (count === 1) {
      this.triggerGesture('1_tap', 1);
    } else if (count === 2) {
      this.triggerGesture('2_taps', 2);
    } else if (count === 3) {
      this.triggerGesture('3_taps', 3);
    } else if (count === 4) {
      this.triggerGesture('4_taps', 4);
    } else if (count === 5) {
      this.triggerGesture('5_taps', 5);
    } else if (count >= 6) {
      this.triggerGesture('6_taps', 6);
    }

    this.tapTimestamps = [];
  }

  private triggerGesture(gesture: GestureType, count: number) {
    // Check if player is currently in after_answer_menu
    const handledByMenu = AudioPlayer.handleAfterAnswerGesture(count);
    if (handledByMenu) {
      return;
    }

    this.gestureListeners.forEach(listener => listener(gesture, count));
  }

  private clearGroupTimer() {
    if (this.groupTimeout) {
      clearTimeout(this.groupTimeout);
      this.groupTimeout = null;
    }
  }

  public subscribeGestures(listener: GestureDetectedListener): () => void {
    this.gestureListeners.add(listener);
    return () => this.gestureListeners.delete(listener);
  }

  public subscribeWaveform(listener: WaveformListener): () => void {
    this.waveformListeners.add(listener);
    return () => this.waveformListeners.delete(listener);
  }
}

export const DeskTapEngine = new DeskTapGestureEngine();
