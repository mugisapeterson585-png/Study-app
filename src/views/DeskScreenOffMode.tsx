import React, { useState, useEffect } from 'react';
import { 
  Headphones, 
  Activity, 
  Moon, 
  X, 
  Volume2, 
  Play, 
  Pause, 
  SkipForward, 
  RotateCcw,
  Sparkles
} from 'lucide-react';
import { DeskTapEngine } from '../gestures/DeskTapEngine';
import { AudioPlayer, PlayerProgress } from '../audio/AudioPlayer';
import { EarbudsGuard } from '../audio/EarbudsAudioGuard';
import { GestureType, AnswerItem } from '../types';

interface DeskScreenOffModeProps {
  onExit: () => void;
  currentAnswer: AnswerItem | null;
  onNextAnswer: () => void;
  onPrevAnswer: () => void;
}

export const DeskScreenOffMode: React.FC<DeskScreenOffModeProps> = ({
  onExit,
  currentAnswer,
  onNextAnswer,
  onPrevAnswer,
}) => {
  const [timeStr, setTimeStr] = useState<string>('');
  const [lastGestureLabel, setLastGestureLabel] = useState<string | null>(null);
  const [playerProgress, setPlayerProgress] = useState<PlayerProgress>({
    currentAnswerId: null,
    state: 'idle',
    currentBlockIndex: 0,
    totalBlocks: 0,
    activeBlock: null,
    isMathSlowdownActive: false,
    menuCountdownRemaining: 0,
  });

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTimeStr(
        now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      );
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const unsubPlayer = AudioPlayer.subscribe(setPlayerProgress);

    const unsubGestures = DeskTapEngine.subscribeGestures((gesture: GestureType) => {
      if (gesture === '1_tap') {
        setLastGestureLabel('1 Tap: Play/Pause');
        AudioPlayer.togglePlayPause();
      } else if (gesture === '2_taps') {
        setLastGestureLabel('2 Taps: Next Answer');
        onNextAnswer();
      } else if (gesture === '3_taps') {
        setLastGestureLabel('3 Taps: Prev Answer');
        onPrevAnswer();
      } else if (gesture === 'tap_pause_tap') {
        setLastGestureLabel('Tap-Pause-Tap: Repeat');
        if (currentAnswer) AudioPlayer.playAnswer(currentAnswer);
      } else if (gesture === 'long_knock') {
        setLastGestureLabel('Long Knock: Summary');
        if (currentAnswer) AudioPlayer.playAnswer(currentAnswer, 'short');
      } else if (gesture === '4_taps') {
        setLastGestureLabel('4 Taps: Silent & Wake');
        AudioPlayer.stopImmediate();
        onExit();
      }

      setTimeout(() => setLastGestureLabel(null), 3000);
    });

    return () => {
      unsubPlayer();
      unsubGestures();
    };
  }, [currentAnswer, onNextAnswer, onPrevAnswer, onExit]);

  return (
    <div
      onClick={() => DeskTapEngine.triggerSimulatedKnock('1_tap')}
      className="fixed inset-0 z-50 bg-black text-slate-400 select-none flex flex-col justify-between p-6 cursor-pointer overflow-hidden transition-all duration-300"
    >
      {/* Top minimal status bar */}
      <div className="flex items-center justify-between text-xs opacity-40 hover:opacity-100 transition-opacity">
        <div className="flex items-center gap-2">
          <Moon size={14} className="text-blue-400" />
          <span className="font-mono text-slate-500">DESK SCREEN-OFF MODE</span>
        </div>

        <button
          onClick={(e) => {
            e.stopPropagation();
            onExit();
          }}
          className="p-2 rounded-full bg-white/10 text-white/60 hover:text-white cursor-pointer"
          title="Wake screen"
        >
          <X size={16} />
        </button>
      </div>

      {/* Center OLED Minimal Clock & Knock Feedback */}
      <div className="flex flex-col items-center justify-center text-center my-auto">
        <span className="text-6xl sm:text-8xl font-thin font-mono tracking-wider text-slate-600">
          {timeStr}
        </span>

        {currentAnswer && (
          <div className="mt-4 flex items-center gap-2 text-xs text-slate-500 font-mono">
            <span>ANSWER #{currentAnswer.id}</span>
            <span>·</span>
            <span>{currentAnswer.subject}</span>
          </div>
        )}

        {/* Live Audio State */}
        <div className="mt-4 flex items-center gap-2 text-xs">
          {playerProgress.state === 'playing' ? (
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-blue-950/60 text-blue-400 border border-blue-800/40 animate-pulse">
              <Volume2 size={14} />
              <span>Speaking in Earbuds</span>
            </div>
          ) : (
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-900 text-slate-500 border border-slate-800">
              <Headphones size={14} />
              <span>Earbuds Ready · Phone Resting Flat</span>
            </div>
          )}
        </div>

        {/* Gesture Recognition Banner */}
        {lastGestureLabel && (
          <div className="mt-6 px-4 py-2 rounded-2xl bg-blue-500/20 border border-blue-500/50 text-blue-300 text-xs font-mono font-bold animate-bounce">
            {lastGestureLabel}
          </div>
        )}
      </div>

      {/* Bottom hint */}
      <div className="text-center text-[11px] text-slate-600 pb-2">
        <span>Knock on table: 1 tap = Play/Pause · 2 taps = Next · 3 taps = Prev · Tap screen to simulate</span>
      </div>
    </div>
  );
};
