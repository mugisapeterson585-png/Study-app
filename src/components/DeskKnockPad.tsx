import React, { useState, useEffect, useRef } from 'react';
import { DeskTapEngine, TapSpikeData } from '../gestures/DeskTapEngine';
import { GestureType } from '../types';
import { NeuCard } from './neumorphic/NeuCard';
import { NeuButton } from './neumorphic/NeuButton';
import { HandMetal, Volume2, Sparkles, CheckCircle2 } from 'lucide-react';

interface DeskKnockPadProps {
  lastGesture: { type: GestureType; label: string; timestamp: number } | null;
  onSimulateGesture?: (gesture: GestureType) => void;
}

export const DeskKnockPad: React.FC<DeskKnockPadProps> = ({
  lastGesture,
  onSimulateGesture,
}) => {
  const [knocks, setKnocks] = useState<{ id: number; x: number; y: number }[]>([]);
  const [waveformData, setWaveformData] = useState<number[]>([]);
  const [isPressingLong, setIsPressingLong] = useState(false);
  const longPressTimer = useRef<any>(null);

  useEffect(() => {
    // Subscribe to live spikes
    const unsubscribe = DeskTapEngine.subscribeWaveform((spike: TapSpikeData) => {
      setWaveformData(prev => {
        const next = [...prev, Math.min(100, (spike.magnitude / 5) * 100)];
        if (next.length > 30) next.shift();
        return next;
      });
    });

    return () => unsubscribe();
  }, []);

  const handlePadTap = (e: React.MouseEvent<HTMLDivElement> | React.TouchEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    let clientX = 0;
    let clientY = 0;

    if ('touches' in e && e.touches.length > 0) {
      clientX = e.touches[0].clientX;
      clientY = e.touches[0].clientY;
    } else if ('clientX' in e) {
      clientX = (e as React.MouseEvent).clientX;
      clientY = (e as React.MouseEvent).clientY;
    }

    const x = clientX - rect.left;
    const y = clientY - rect.top;

    const newKnock = { id: Date.now() + Math.random(), x, y };
    setKnocks(prev => [...prev.slice(-4), newKnock]);

    // Trigger tap in gesture engine
    DeskTapEngine.triggerSimulatedKnock();

    setTimeout(() => {
      setKnocks(prev => prev.filter(k => k.id !== newKnock.id));
    }, 600);
  };

  const handleMouseDown = () => {
    longPressTimer.current = setTimeout(() => {
      setIsPressingLong(true);
      DeskTapEngine.triggerSimulatedLongKnock();
    }, 800);
  };

  const handleMouseUp = () => {
    if (longPressTimer.current) {
      clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
    setIsPressingLong(false);
  };

  return (
    <NeuCard elevation="raised" rounded="2xl" className="overflow-hidden">
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <HandMetal size={16} className="text-blue-500" />
          <span className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
            Desk Knock Surface & Sensor
          </span>
        </div>
        {lastGesture && Date.now() - lastGesture.timestamp < 3000 && (
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-blue-100 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300 text-xs font-semibold animate-pulse">
            <CheckCircle2 size={12} />
            <span>Recognized: {lastGesture.label}</span>
          </div>
        )}
      </div>

      <p className="text-xs text-slate-500 dark:text-slate-400 mb-3 leading-relaxed">
        Physical desk knocks are detected by phone accelerometer when resting flat. You can also tap this soft surface pad to test gestures silently.
      </p>

      {/* Interactive Desk Knock Pad */}
      <div
        onMouseDown={handleMouseDown}
        onMouseUp={handleMouseUp}
        onTouchStart={handleMouseDown}
        onTouchEnd={handleMouseUp}
        onClick={handlePadTap}
        className={`
          relative w-full h-32 rounded-2xl neu-pressed cursor-pointer select-none overflow-hidden
          flex flex-col items-center justify-center transition-all duration-150
          border border-slate-300/30 dark:border-slate-700/30
          ${isPressingLong ? 'bg-blue-500/10' : 'bg-[#DDE2EA] dark:bg-[#25282F]'}
        `}
      >
        {/* Subtle woodgrain/desk texture lines */}
        <div className="absolute inset-0 opacity-15 pointer-events-none bg-[radial-gradient(#94a3b8_1px,transparent_1px)] [background-size:16px_16px]" />

        {/* Live knock ripples */}
        {knocks.map(k => (
          <div
            key={k.id}
            className="absolute w-12 h-12 -ml-6 -mt-6 rounded-full border-2 border-blue-500/80 bg-blue-400/20 pointer-events-none animate-knock"
            style={{ left: k.x, top: k.y }}
          />
        ))}

        <div className="relative z-10 flex flex-col items-center pointer-events-none text-center px-4">
          <div className="w-10 h-10 rounded-full neu-raised flex items-center justify-center text-slate-600 dark:text-slate-300 mb-1.5">
            <HandMetal size={20} className={isPressingLong ? 'text-blue-500 scale-110 transition-transform' : ''} />
          </div>
          <span className="text-xs font-semibold text-slate-700 dark:text-slate-200">
            {isPressingLong ? 'Sustained Knock Detected!' : 'Tap desk or tap here to knock'}
          </span>
          <span className="text-[11px] text-slate-400 dark:text-slate-500 mt-0.5">
            1 tap = Play/Pause · 2 taps = Next · 3 taps = Prev · Hold = Summary
          </span>
        </div>

        {/* Live waveform sparkline at bottom */}
        <div className="absolute bottom-1.5 left-3 right-3 h-6 flex items-end gap-1 opacity-60 pointer-events-none">
          {waveformData.map((val, idx) => (
            <div
              key={idx}
              className="flex-1 bg-blue-500/60 rounded-t-sm transition-all duration-75"
              style={{ height: `${Math.max(10, val)}%` }}
            />
          ))}
        </div>
      </div>

      {/* Quick gesture buttons for instantaneous testing */}
      <div className="grid grid-cols-4 gap-2 mt-3">
        <button
          onClick={() => DeskTapEngine.triggerSimulatedKnock('1_tap')}
          className="px-2 py-1.5 rounded-xl neu-raised hover:neu-pressed text-[11px] font-semibold text-slate-700 dark:text-slate-300 text-center transition-all cursor-pointer"
        >
          1 Tap
        </button>
        <button
          onClick={() => DeskTapEngine.triggerSimulatedKnock('2_taps')}
          className="px-2 py-1.5 rounded-xl neu-raised hover:neu-pressed text-[11px] font-semibold text-slate-700 dark:text-slate-300 text-center transition-all cursor-pointer"
        >
          2 Taps
        </button>
        <button
          onClick={() => DeskTapEngine.triggerSimulatedKnock('3_taps')}
          className="px-2 py-1.5 rounded-xl neu-raised hover:neu-pressed text-[11px] font-semibold text-slate-700 dark:text-slate-300 text-center transition-all cursor-pointer"
        >
          3 Taps
        </button>
        <button
          onClick={() => DeskTapEngine.triggerSimulatedKnock('long_knock')}
          className="px-2 py-1.5 rounded-xl neu-raised hover:neu-pressed text-[11px] font-semibold text-slate-700 dark:text-slate-300 text-center transition-all cursor-pointer"
        >
          Long Knock
        </button>
      </div>
    </NeuCard>
  );
};
