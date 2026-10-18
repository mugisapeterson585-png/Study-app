import React from 'react';
import { 
  Headphones, 
  VolumeX, 
  Activity, 
  BatteryCharging, 
  Wifi, 
  WifiOff, 
  Clock, 
  Moon,
  Sparkles
} from 'lucide-react';
import { EarbudsGuard } from '../audio/EarbudsAudioGuard';
import { AppSettings, StudySessionState } from '../types';

interface StatusPillProps {
  hasEarbuds: boolean;
  onToggleEarbuds: () => void;
  isSensorActive: boolean;
  onToggleSensor: () => void;
  sessionState: StudySessionState;
  isOnline: boolean;
  settings: AppSettings;
  onOpenDeskMode: () => void;
}

export const StatusPill: React.FC<StatusPillProps> = ({
  hasEarbuds,
  onToggleEarbuds,
  isSensorActive,
  onToggleSensor,
  sessionState,
  isOnline,
  settings,
  onOpenDeskMode,
}) => {
  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <header className="sticky top-0 z-30 px-3 py-2.5 backdrop-blur-md bg-[#E0E5EC]/85 dark:bg-[#2B2E35]/85 transition-colors">
      <div className="max-w-4xl mx-auto flex items-center justify-between gap-2">
        {/* Left: Brand + Earbuds status */}
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full neu-pressed text-xs">
            <span className="font-bold tracking-tight text-slate-800 dark:text-slate-100 hidden sm:inline">
              SilentStudy
            </span>
            <span className="text-slate-400 hidden sm:inline" aria-hidden="true">·</span>
            <button
              onClick={onToggleEarbuds}
              title={hasEarbuds ? 'Earbuds connected. Tap to simulate disconnect.' : 'Earbuds disconnected! Speaker blocked.'}
              className={`flex items-center gap-1.5 font-medium transition-colors cursor-pointer ${
                hasEarbuds 
                  ? 'text-blue-600 dark:text-blue-400 font-semibold' 
                  : 'text-amber-600 dark:text-amber-400 font-bold animate-pulse'
              }`}
            >
              {hasEarbuds ? <Headphones size={13} /> : <VolumeX size={13} />}
              <span className="truncate max-w-[110px] sm:max-w-[140px]">
                {hasEarbuds ? 'AirPods Pro' : 'Speaker Guard'}
              </span>
            </button>
          </div>
        </div>

        {/* Center: Live Session & Sensor state */}
        <div className="flex items-center gap-2">
          {sessionState.isActive && (
            <div className="flex items-center gap-1 px-2.5 py-1 rounded-full neu-pressed text-xs font-mono text-emerald-600 dark:text-emerald-400">
              <Clock size={12} />
              <span>{formatTime(sessionState.durationSeconds)}</span>
            </div>
          )}

          <button
            onClick={onToggleSensor}
            title={isSensorActive ? 'Desk Sensor Active (Tap phone or desk to control)' : 'Desk Sensor Paused'}
            className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-xs transition-all cursor-pointer ${
              isSensorActive 
                ? 'neu-pressed text-blue-600 dark:text-blue-400 font-medium' 
                : 'neu-raised text-slate-500 opacity-60'
            }`}
          >
            <Activity size={12} className={isSensorActive ? 'text-blue-500' : ''} />
            <span className="hidden xs:inline">Sensor</span>
          </button>
        </div>

        {/* Right: Desk Screen-Off Button & Network */}
        <div className="flex items-center gap-2">
          <button
            onClick={onOpenDeskMode}
            title="Desk / Screen-Off Mode (Phone flat on table, controlled only by taps)"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full neu-raised hover:neu-pressed text-xs font-semibold text-slate-700 dark:text-slate-200 transition-all cursor-pointer"
          >
            <Moon size={13} className="text-indigo-500" />
            <span className="hidden sm:inline">Desk Mode</span>
          </button>

          <div
            title={isOnline ? 'Online: AI connected' : 'Offline mode: Room database & offline queue active'}
            className="flex items-center p-1.5 rounded-full neu-pressed text-slate-500 dark:text-slate-400"
          >
            {isOnline ? <Wifi size={13} className="text-slate-600 dark:text-slate-300" /> : <WifiOff size={13} className="text-amber-500" />}
          </div>
        </div>
      </div>
    </header>
  );
};
