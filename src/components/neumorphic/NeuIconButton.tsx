import React from 'react';

interface NeuIconButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'raised' | 'pressed' | 'accent';
  size?: 'sm' | 'md' | 'lg';
  active?: boolean;
  children: React.ReactNode;
}

export const NeuIconButton: React.FC<NeuIconButtonProps> = ({
  variant = 'raised',
  size = 'md',
  active = false,
  className = '',
  children,
  disabled,
  ...props
}) => {
  const sizeClasses = {
    sm: 'w-10 h-10 min-w-[40px]',
    md: 'w-12 h-12 min-w-[48px]',
    lg: 'w-14 h-14 min-w-[56px]',
  }[size];

  const actualVariant = active ? 'pressed' : variant;

  const variantClasses = {
    raised: 'neu-raised active:neu-pressed text-slate-700 dark:text-slate-300 hover:text-blue-600 dark:hover:text-blue-400',
    pressed: 'neu-pressed text-blue-600 dark:text-blue-400',
    accent: 'bg-blue-600 text-white shadow-lg shadow-blue-500/30 active:scale-[0.96]',
  }[actualVariant];

  return (
    <button
      className={`
        inline-flex items-center justify-center rounded-2xl cursor-pointer
        transition-all duration-150 outline-none focus-visible:ring-2 focus-visible:ring-blue-500/50
        disabled:opacity-40 disabled:pointer-events-none select-none
        ${sizeClasses}
        ${variantClasses}
        ${className}
      `}
      disabled={disabled}
      {...props}
    >
      {children}
    </button>
  );
};
