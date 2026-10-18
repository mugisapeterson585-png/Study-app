import React from 'react';

interface NeuButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'raised' | 'pressed' | 'accent' | 'convex';
  size?: 'sm' | 'md' | 'lg';
  fullWidth?: boolean;
  children: React.ReactNode;
}

export const NeuButton: React.FC<NeuButtonProps> = ({
  variant = 'raised',
  size = 'md',
  fullWidth = false,
  className = '',
  children,
  disabled,
  ...props
}) => {
  const sizeClasses = {
    sm: 'px-3 py-2 text-xs min-h-[40px]',
    md: 'px-5 py-3 text-sm min-h-[48px]',
    lg: 'px-6 py-3.5 text-base min-h-[56px]',
  }[size];

  const variantClasses = {
    raised: 'neu-raised active:neu-pressed text-slate-700 dark:text-slate-200 hover:text-slate-900 dark:hover:text-white',
    pressed: 'neu-pressed text-blue-600 dark:text-blue-400 font-semibold',
    accent: 'bg-blue-600 text-white shadow-lg shadow-blue-500/30 active:scale-[0.98] font-semibold hover:bg-blue-700',
    convex: 'neu-convex active:neu-pressed text-slate-800 dark:text-slate-100 font-medium',
  }[variant];

  return (
    <button
      className={`
        relative inline-flex items-center justify-center font-medium rounded-2xl
        transition-all duration-200 outline-none focus-visible:ring-2 focus-visible:ring-blue-500/50
        disabled:opacity-50 disabled:pointer-events-none cursor-pointer
        ${sizeClasses}
        ${variantClasses}
        ${fullWidth ? 'w-full' : ''}
        ${className}
      `}
      disabled={disabled}
      {...props}
    >
      {children}
    </button>
  );
};
