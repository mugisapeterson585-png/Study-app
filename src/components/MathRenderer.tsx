import React, { useMemo } from 'react';
import katex from 'katex';

interface MathRendererProps {
  content: string;
  isBlockMath?: boolean;
  className?: string;
}

export const MathRenderer: React.FC<MathRendererProps> = ({
  content,
  isBlockMath = false,
  className = '',
}) => {
  const renderedHtml = useMemo(() => {
    if (!content) return '';

    // If string is already purely a LaTeX formula or surrounded by $$ or $
    const trimmed = content.trim();

    if (isBlockMath || (trimmed.startsWith('$$') && trimmed.endsWith('$$'))) {
      const mathStr = trimmed.replace(/^\$\$/, '').replace(/\$\$$/, '');
      try {
        return katex.renderToString(mathStr, {
          displayMode: true,
          throwOnError: false,
        });
      } catch {
        return `<span class="font-mono text-sm">${content}</span>`;
      }
    }

    // Process inline and block math within mixed text
    // Replace $$...$$ first, then $...$
    try {
      let result = content;

      // Handle $$ ... $$
      result = result.replace(/\$\$([\s\S]*?)\$\$/g, (_, math) => {
        try {
          return katex.renderToString(math, { displayMode: true, throwOnError: false });
        } catch {
          return math;
        }
      });

      // Handle $ ... $
      result = result.replace(/\$([^\$\n]+?)\$/g, (_, math) => {
        try {
          return katex.renderToString(math, { displayMode: false, throwOnError: false });
        } catch {
          return math;
        }
      });

      // Simple markdown bold/italic parsing for clean display
      result = result.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
      result = result.replace(/\*(.*?)\*/g, '<em>$1</em>');

      return result;
    } catch {
      return content;
    }
  }, [content, isBlockMath]);

  return (
    <div
      className={`leading-relaxed text-slate-800 dark:text-slate-100 ${className}`}
      dangerouslySetInnerHTML={{ __html: renderedHtml }}
    />
  );
};
