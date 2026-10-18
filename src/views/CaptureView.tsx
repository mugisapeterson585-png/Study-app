import React, { useState } from 'react';
import { CameraScanner } from '../components/CameraScanner';
import { AppSettings, AnswerItem, AiProfile } from '../types';
import { AiProviderService } from '../ai/AiProvider';
import { AppStorage } from '../data/storage';
import { AudioPlayer } from '../audio/AudioPlayer';
import { NeuCard } from '../components/neumorphic/NeuCard';
import { Cpu, AlertTriangle, ArrowRight, CheckCircle2 } from 'lucide-react';

interface CaptureViewProps {
  settings: AppSettings;
  activeProfile: AiProfile;
  fallbackProfile?: AiProfile;
  onAnswerCreated: (newAnswer: AnswerItem) => void;
  onOpenSettingsAi: () => void;
}

export const CaptureView: React.FC<CaptureViewProps> = ({
  settings,
  activeProfile,
  fallbackProfile,
  onAnswerCreated,
  onOpenSettingsAi,
}) => {
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successNotice, setSuccessNotice] = useState<string | null>(null);

  const handlePhotosReady = async (photos: string[], promptNote: string) => {
    setIsAnalyzing(true);
    setErrorMsg(null);
    setSuccessNotice(null);

    try {
      // Execute AI structured output analysis
      const result = await AiProviderService.answer(
        activeProfile,
        {
          images: photos,
          questionPrompt: promptNote,
        },
        fallbackProfile
      );

      // Save into Room-like database with permanent auto-increment ID
      const newAnswer = AppStorage.addAnswer({
        subject: result.subject || 'General',
        questionText: result.question_text || 'Photo Question',
        imagePaths: photos,
        shortAnswer: result.short_answer,
        blocks: result.blocks,
        simpleExplanation: result.simple_explanation,
      });

      setSuccessNotice(`Saved as permanent answer #${newAnswer.id}`);
      onAnswerCreated(newAnswer);

      // Play chime in earbuds
      AudioPlayer.playChime();
    } catch (err: any) {
      console.error('AI Analysis failed:', err);
      setErrorMsg(
        err.message ||
        'Failed to get answer from AI provider. Check your API key or network in Settings > AI.'
      );
    } finally {
      setIsAnalyzing(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto px-3 py-4 flex flex-col gap-4">
      {/* Active AI Provider Status Indicator */}
      <div className="flex items-center justify-between px-3 py-2 rounded-2xl neu-pressed text-xs">
        <div className="flex items-center gap-2">
          <Cpu size={14} className="text-blue-500" />
          <span className="text-slate-500 dark:text-slate-400">AI Engine:</span>
          <span className="font-bold text-slate-800 dark:text-slate-200">
            {activeProfile.name}
          </span>
          {fallbackProfile && (
            <span className="text-[11px] text-slate-400 hidden sm:inline">
              (Fallback: {fallbackProfile.name})
            </span>
          )}
        </div>

        <button
          onClick={onOpenSettingsAi}
          className="text-blue-600 dark:text-blue-400 font-semibold hover:underline cursor-pointer"
        >
          Change AI Provider
        </button>
      </div>

      {/* Error Banner */}
      {errorMsg && (
        <NeuCard elevation="raised" rounded="2xl" className="p-3 bg-red-50/40 dark:bg-red-950/40 border border-red-500/30">
          <div className="flex items-start gap-2.5">
            <AlertTriangle size={18} className="text-red-500 shrink-0 mt-0.5" />
            <div className="flex-1">
              <span className="text-xs font-bold text-red-700 dark:text-red-300 block mb-0.5">
                AI Provider Error
              </span>
              <p className="text-xs text-red-600 dark:text-red-400 leading-relaxed">
                {errorMsg}
              </p>
              <button
                onClick={onOpenSettingsAi}
                className="mt-2 text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1 cursor-pointer"
              >
                <span>Check API Key in Settings</span>
                <ArrowRight size={12} />
              </button>
            </div>
          </div>
        </NeuCard>
      )}

      {/* Success Banner */}
      {successNotice && (
        <NeuCard elevation="raised" rounded="2xl" className="p-3 bg-emerald-50/40 dark:bg-emerald-950/40 border border-emerald-500/30 flex items-center gap-2 text-emerald-700 dark:text-emerald-300 text-xs font-bold">
          <CheckCircle2 size={16} className="text-emerald-500" />
          <span>{successNotice}</span>
        </NeuCard>
      )}

      {/* Camera Capture Screen */}
      <CameraScanner
        settings={settings}
        onPhotosReady={handlePhotosReady}
        isAnalyzing={isAnalyzing}
      />
    </div>
  );
};
