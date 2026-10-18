import React from 'react';

interface NeuCardProps extends React.HTMLAttributes<HTMLDivElement> {
  elevation?: 'flat' | 'raised' | 'raised-sm' | 'raised-lg' | 'pressed' | 'convex';
  rounded?: 'xl' | '2xl' | '3xl';
  children: React.ReactNode;
}

export const NeuCard: React.FC<NeuCardProps> = ({
  elevation = 'raised',
  rounded = '2xl',
  className = '',
  children,
  ...props
}) => {
  const elevationClasses = {
    flat: 'bg-[#E0E5EC] dark:bg-[#2B2E35] border border-slate-300/40 dark:border-slate-700/40',
    raised: 'neu-raised',
    'raised-sm': 'neu-raised-sm',
    'raised-lg': 'neu-raised-lg',
    pressed: 'neu-pressed',
    convex: 'neu-convex',
  }[elevation];

  const roundedClasses = {
    xl: 'rounded-xl',
    '2xl': 'rounded-2xl',
    '3xl': 'rounded-3xl',
  }[rounded];

  return (
    <div
      className={`
        relative p-4 transition-all duration-200
        bg-[#E0E5EC] dark:bg-[#2B2E35]
        ${roundedClasses}
        ${elevationClasses}
        ${className}
      `}
      {...props}
    >
      {children}
    </div>
  );
};
