import React from 'react';

interface NeuSliderProps {
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (value: number) => void;
  label?: string;
  valueDisplay?: string | number;
  unit?: string;
}

export const NeuSlider: React.FC<NeuSliderProps> = ({
  value,
  min,
  max,
  step = 1,
  onChange,
  label,
  valueDisplay,
  unit = '',
}) => {
  const percentage = Math.min(100, Math.max(0, ((value - min) / (max - min)) * 100));

  return (
    <div className="w-full flex flex-col gap-2 py-2">
      <div className="flex justify-between items-center text-xs">
        {label && <span className="font-semibold text-slate-700 dark:text-slate-300">{label}</span>}
        <span className="font-mono text-blue-600 dark:text-blue-400 font-medium">
          {valueDisplay !== undefined ? valueDisplay : value} {unit}
        </span>
      </div>
      <div className="relative w-full h-4 rounded-full neu-pressed flex items-center px-1">
        <div
          className="h-2 rounded-full bg-blue-500/70 dark:bg-blue-600/80 transition-all duration-75"
          style={{ width: `${percentage}%` }}
        />
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={(e) => onChange(parseFloat(e.target.value))}
          className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10"
        />
        <div
          className="absolute top-1/2 -translate-y-1/2 w-6 h-6 rounded-full neu-raised bg-[#E0E5EC] dark:bg-[#2B2E35] border border-white/40 dark:border-slate-700/50 pointer-events-none transition-all duration-75 flex items-center justify-center"
          style={{ left: `calc(${percentage}% - 12px)` }}
        >
          <div className="w-2 h-2 rounded-full bg-blue-600 dark:bg-blue-400" />
        </div>
      </div>
    </div>
  );
};
