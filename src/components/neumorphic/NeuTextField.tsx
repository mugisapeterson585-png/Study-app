import React from 'react';

interface NeuTextFieldProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  startIcon?: React.ReactNode;
  endIcon?: React.ReactNode;
}

export const NeuTextField: React.FC<NeuTextFieldProps> = ({
  label,
  error,
  helperText,
  startIcon,
  endIcon,
  className = '',
  disabled,
  ...props
}) => {
  return (
    <div className={`w-full flex flex-col gap-1.5 ${disabled ? 'opacity-50' : ''}`}>
      {label && (
        <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
          {label}
        </label>
      )}
      <div className="relative flex items-center w-full">
        {startIcon && (
          <div className="absolute left-3.5 flex items-center pointer-events-none text-slate-500 dark:text-slate-400 z-10">
            {startIcon}
          </div>
        )}
        <input
          disabled={disabled}
          className={`
            w-full neu-pressed rounded-2xl py-3 text-sm
            bg-[#E0E5EC] dark:bg-[#2B2E35] text-slate-800 dark:text-slate-100
            placeholder:text-slate-400 dark:placeholder:text-slate-500
            outline-none transition-all duration-150
            focus:ring-2 focus:ring-blue-500/40
            ${startIcon ? 'pl-10' : 'pl-4'}
            ${endIcon ? 'pr-10' : 'pr-4'}
            ${error ? 'ring-2 ring-red-500/50' : ''}
            ${className}
          `}
          {...props}
        />
        {endIcon && (
          <div className="absolute right-3 flex items-center z-10 text-slate-500 dark:text-slate-400">
            {endIcon}
          </div>
        )}
      </div>
      {error && <span className="text-xs text-red-500 font-medium pl-1">{error}</span>}
      {!error && helperText && (
        <span className="text-xs text-slate-500 dark:text-slate-400 pl-1">{helperText}</span>
      )}
    </div>
  );
};
