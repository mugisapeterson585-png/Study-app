import React from 'react';

interface NeuToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label?: string;
  sublabel?: string;
  disabled?: boolean;
}

export const NeuToggle: React.FC<NeuToggleProps> = ({
  checked,
  onChange,
  label,
  sublabel,
  disabled = false,
}) => {
  return (
    <label className={`flex items-center justify-between cursor-pointer select-none py-2 gap-4 ${disabled ? 'opacity-50 pointer-events-none' : ''}`}>
      {(label || sublabel) && (
        <div className="flex flex-col flex-1">
          {label && <span className="text-sm font-semibold text-slate-800 dark:text-slate-200">{label}</span>}
          {sublabel && <span className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">{sublabel}</span>}
        </div>
      )}
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`
          relative w-14 h-8 rounded-full p-1 transition-all duration-300 outline-none
          neu-pressed flex items-center shrink-0
          ${checked ? 'bg-blue-50/50 dark:bg-blue-950/40' : ''}
        `}
      >
        <span
          className={`
            block w-6 h-6 rounded-full transition-transform duration-200
            ${checked 
              ? 'translate-x-6 bg-blue-600 shadow-md shadow-blue-500/50' 
              : 'translate-x-0 neu-raised bg-slate-100 dark:bg-slate-700'}
          `}
        />
      </button>
    </label>
  );
};
