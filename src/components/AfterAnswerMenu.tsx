import React from 'react';
import { RotateCcw, SkipForward, HelpCircle, Volume2 } from 'lucide-react';
import { NeuCard } from './neumorphic/NeuCard';
import { NeuButton } from './neumorphic/NeuButton';

interface AfterAnswerMenuProps {
  countdownSeconds: number;
  onRepeat: () => void;
  onNext: () => void;
  onSimpler: () => void;
  onDismiss: () => void;
}

export const AfterAnswerMenu: React.FC<AfterAnswerMenuProps> = ({
  countdownSeconds,
  onRepeat,
  onNext,
  onSimpler,
  onDismiss,
}) => {
  return (
    <NeuCard elevation="raised-lg" rounded="3xl" className="border border-blue-500/20 bg-blue-50/20 dark:bg-blue-950/20 p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <Volume2 size={16} className="text-blue-500 animate-pulse" />
          <span className="text-xs font-bold uppercase tracking-wider text-blue-700 dark:text-blue-300">
            After-Answer Menu (Earbuds Audio)
          </span>
        </div>
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full neu-pressed text-xs font-mono font-semibold text-blue-600 dark:text-blue-400">
          <span>{countdownSeconds}s</span>
        </div>
      </div>

      <p className="text-xs text-slate-600 dark:text-slate-300 mb-4 leading-relaxed">
        "Tap once to repeat. Tap twice for the next answer." Knock on desk now or pick an action:
      </p>

      <div className="grid grid-cols-3 gap-2.5">
        <button
          onClick={onRepeat}
          className="flex flex-col items-center justify-center p-3 rounded-2xl neu-raised hover:neu-pressed transition-all cursor-pointer text-center"
        >
          <div className="w-8 h-8 rounded-full neu-pressed flex items-center justify-center mb-1 text-blue-600 dark:text-blue-400">
            <RotateCcw size={14} />
          </div>
          <span className="text-xs font-bold text-slate-800 dark:text-slate-200">1 Tap</span>
          <span className="text-[10px] text-slate-500 dark:text-slate-400">Repeat</span>
        </button>

        <button
          onClick={onNext}
          className="flex flex-col items-center justify-center p-3 rounded-2xl neu-raised hover:neu-pressed transition-all cursor-pointer text-center"
        >
          <div className="w-8 h-8 rounded-full neu-pressed flex items-center justify-center mb-1 text-emerald-600 dark:text-emerald-400">
            <SkipForward size={14} />
          </div>
          <span className="text-xs font-bold text-slate-800 dark:text-slate-200">2 Taps</span>
          <span className="text-[10px] text-slate-500 dark:text-slate-400">Next</span>
        </button>

        <button
          onClick={onSimpler}
          className="flex flex-col items-center justify-center p-3 rounded-2xl neu-raised hover:neu-pressed transition-all cursor-pointer text-center"
        >
          <div className="w-8 h-8 rounded-full neu-pressed flex items-center justify-center mb-1 text-purple-600 dark:text-purple-400">
            <HelpCircle size={14} />
          </div>
          <span className="text-xs font-bold text-slate-800 dark:text-slate-200">3 Taps</span>
          <span className="text-[10px] text-slate-500 dark:text-slate-400">Simpler</span>
        </button>
      </div>
    </NeuCard>
  );
};
