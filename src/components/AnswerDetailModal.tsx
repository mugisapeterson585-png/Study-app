import React, { useState, useEffect } from 'react';
import { 
  AnswerItem, 
  PlaybackDetailMode, 
  AnswerBlock 
} from '../types';
import { 
  X, 
  Play, 
  Pause, 
  Star, 
  Volume2, 
  VolumeX, 
  RotateCcw, 
  MessageSquareQuote, 
  Sparkles, 
  Image as ImageIcon,
  CheckCircle,
  HelpCircle,
  ChevronRight
} from 'lucide-react';
import { NeuCard } from './neumorphic/NeuCard';
import { NeuButton } from './neumorphic/NeuButton';
import { NeuIconButton } from './neumorphic/NeuIconButton';
import { MathRenderer } from './MathRenderer';
import { AudioPlayer, PlayerProgress } from '../audio/AudioPlayer';
import { EarbudsGuard } from '../audio/EarbudsAudioGuard';
import { AfterAnswerMenu } from './AfterAnswerMenu';

interface AnswerDetailModalProps {
  answer: AnswerItem;
  isOpen: boolean;
  onClose: () => void;
  onToggleStar: (id: number) => void;
  onNextAnswer?: () => void;
  onAskFollowUp?: (answer: AnswerItem, query: string) => void;
}

export const AnswerDetailModal: React.FC<AnswerDetailModalProps> = ({
  answer,
  isOpen,
  onClose,
  onToggleStar,
  onNextAnswer,
  onAskFollowUp,
}) => {
  const [activeTab, setActiveTab] = useState<PlaybackDetailMode>('detailed');
  const [playerProgress, setPlayerProgress] = useState<PlayerProgress>({
    currentAnswerId: null,
    state: 'idle',
    currentBlockIndex: 0,
    totalBlocks: 0,
    activeBlock: null,
    isMathSlowdownActive: false,
    menuCountdownRemaining: 0,
  });
  const [showPhotos, setShowPhotos] = useState<boolean>(false);
  const [followUpQuery, setFollowUpQuery] = useState<string>('');
  const [isAskingFollowUp, setIsAskingFollowUp] = useState<boolean>(false);
  const [followUpResponse, setFollowUpResponse] = useState<string | null>(null);

  useEffect(() => {
    const unsub = AudioPlayer.subscribe(setPlayerProgress);
    return () => unsub();
  }, []);

  if (!isOpen) return null;

  const isCurrentPlaying = playerProgress.currentAnswerId === answer.id && playerProgress.state === 'playing';
  const hasEarbuds = EarbudsGuard.getStatus().hasEarbudsConnected;

  const handlePlayToggle = () => {
    if (playerProgress.currentAnswerId === answer.id && playerProgress.state === 'playing') {
      AudioPlayer.pause();
    } else {
      AudioPlayer.playAnswer(answer, activeTab);
    }
  };

  const handleTabChange = (tab: PlaybackDetailMode) => {
    setActiveTab(tab);
    if (isCurrentPlaying) {
      AudioPlayer.playAnswer(answer, tab);
    }
  };

  const handleSendFollowUp = () => {
    if (!followUpQuery.trim()) return;
    setIsAskingFollowUp(true);
    setTimeout(() => {
      setFollowUpResponse(
        `Deep Dive: Regarding "${followUpQuery}", the fundamental reason stems from conservation laws and the boundary conditions established in step 2. You can test this by substituting edge values.`
      );
      setIsAskingFollowUp(false);
      setFollowUpQuery('');
    }, 1200);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5 bg-black/50 backdrop-blur-sm overflow-y-auto">
      <div className="w-full max-w-2xl my-auto">
        <NeuCard elevation="raised-lg" rounded="3xl" className="p-4 sm:p-6 max-h-[90vh] flex flex-col overflow-hidden">
          {/* Header */}
          <div className="flex items-center justify-between pb-3 border-b border-slate-300/40 dark:border-slate-700/40">
            <div className="flex items-center gap-2.5">
              <span className="font-mono font-bold text-lg text-blue-600 dark:text-blue-400 neu-pressed px-2.5 py-1 rounded-xl">
                #{answer.id}
              </span>
              <div>
                <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">
                  {answer.subject}
                </span>
                <span className="text-xs text-slate-400 mx-1.5">·</span>
                <span className="text-xs text-slate-400 font-mono">
                  {new Date(answer.createdAt).toLocaleDateString()}
                </span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <NeuIconButton
                size="sm"
                active={answer.starred}
                onClick={() => onToggleStar(answer.id)}
                title="Star answer"
              >
                <Star size={16} className={answer.starred ? 'fill-amber-400 text-amber-500' : ''} />
              </NeuIconButton>

              <NeuIconButton size="sm" onClick={onClose} title="Close">
                <X size={16} />
              </NeuIconButton>
            </div>
          </div>

          {/* Privacy Alert if earbuds missing */}
          {!hasEarbuds && (
            <div className="mt-3 p-2.5 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-between text-xs text-amber-700 dark:text-amber-300">
              <div className="flex items-center gap-2">
                <VolumeX size={15} className="shrink-0" />
                <span>Earbuds-Only Guard Active: Connect AirPods/headphones to listen.</span>
              </div>
              <button
                onClick={() => EarbudsGuard.setSimulatedConnection(true)}
                className="underline font-semibold cursor-pointer shrink-0 ml-2"
              >
                Connect
              </button>
            </div>
          )}

          {/* Question Text */}
          <div className="my-3 p-3 rounded-2xl neu-pressed">
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400 block mb-1">
              Question:
            </span>
            <div className="text-sm font-semibold text-slate-900 dark:text-slate-100 leading-snug">
              <MathRenderer content={answer.questionText} />
            </div>

            {answer.imagePaths && answer.imagePaths.length > 0 && (
              <div className="mt-2 flex items-center gap-2">
                <button
                  onClick={() => setShowPhotos(!showPhotos)}
                  className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1 cursor-pointer"
                >
                  <ImageIcon size={13} />
                  <span>{showPhotos ? 'Hide Photo' : `View Original Photo (${answer.imagePaths.length})`}</span>
                </button>
              </div>
            )}
          </div>

          {/* Optional Original Photos Gallery */}
          {showPhotos && answer.imagePaths && answer.imagePaths.length > 0 && (
            <div className="mb-3 p-2 rounded-2xl neu-pressed flex gap-2 overflow-x-auto">
              {answer.imagePaths.map((img, idx) => (
                <img
                  key={idx}
                  src={img}
                  alt={`Question photo ${idx + 1}`}
                  className="h-28 rounded-xl object-cover border border-slate-300/30"
                />
              ))}
            </div>
          )}

          {/* Mode Switcher Tabs: Detailed / Short / Simple */}
          <div className="flex items-center gap-1 p-1 rounded-2xl neu-pressed mb-3">
            <button
              onClick={() => handleTabChange('detailed')}
              className={`flex-1 py-1.5 text-xs font-semibold rounded-xl transition-all cursor-pointer ${
                activeTab === 'detailed' ? 'neu-raised text-blue-600 dark:text-blue-400' : 'text-slate-500'
              }`}
            >
              Detailed Blocks
            </button>
            <button
              onClick={() => handleTabChange('short')}
              className={`flex-1 py-1.5 text-xs font-semibold rounded-xl transition-all cursor-pointer ${
                activeTab === 'short' ? 'neu-raised text-blue-600 dark:text-blue-400' : 'text-slate-500'
              }`}
            >
              Short TL;DR
            </button>
            <button
              onClick={() => handleTabChange('simple')}
              className={`flex-1 py-1.5 text-xs font-semibold rounded-xl transition-all cursor-pointer ${
                activeTab === 'simple' ? 'neu-raised text-blue-600 dark:text-blue-400' : 'text-slate-500'
              }`}
            >
              Simple Explanation
            </button>
          </div>

          {/* Main Answer Content (Scrollable) */}
          <div className="flex-1 overflow-y-auto pr-1 space-y-3 min-h-[160px]">
            {activeTab === 'short' && (
              <div className="p-4 rounded-2xl neu-raised bg-blue-50/20 dark:bg-blue-950/20">
                <span className="text-[11px] font-bold text-blue-600 dark:text-blue-400 uppercase tracking-wider block mb-1">
                  TL;DR Direct Answer
                </span>
                <MathRenderer content={answer.shortAnswer} className="text-sm font-medium leading-relaxed" />
              </div>
            )}

            {activeTab === 'simple' && (
              <div className="p-4 rounded-2xl neu-raised bg-purple-50/20 dark:bg-purple-950/20">
                <span className="text-[11px] font-bold text-purple-600 dark:text-purple-400 uppercase tracking-wider block mb-1">
                  Explain-Like-I'm-New
                </span>
                <p className="text-sm leading-relaxed text-slate-800 dark:text-slate-200">
                  {answer.simpleExplanation}
                </p>
              </div>
            )}

            {activeTab === 'detailed' && (
              <div className="space-y-2.5">
                {answer.blocks.map((block, idx) => {
                  const isBlockSpeaking = isCurrentPlaying && playerProgress.currentBlockIndex === idx;
                  return (
                    <div
                      key={idx}
                      className={`p-3 rounded-2xl transition-all ${
                        isBlockSpeaking 
                          ? 'neu-pressed border-2 border-blue-500/60 bg-blue-50/30 dark:bg-blue-950/40' 
                          : 'neu-raised'
                      }`}
                    >
                      {block.type === 'step' && block.stepNumber && (
                        <span className="text-[10px] font-mono font-bold text-blue-600 dark:text-blue-400 uppercase tracking-wider block mb-0.5">
                          Step {block.stepNumber}
                        </span>
                      )}
                      {block.type === 'math' && (
                        <span className="text-[10px] font-mono font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider block mb-0.5">
                          Mathematical Calculation
                        </span>
                      )}

                      <MathRenderer content={block.display} className="text-sm" />

                      {/* Earbud TTS Spoken Preview */}
                      <div className="mt-1.5 pt-1.5 border-t border-slate-300/30 dark:border-slate-700/30 flex items-center gap-1.5 text-[11px] text-slate-400 dark:text-slate-500">
                        <Volume2 size={11} className={isBlockSpeaking ? 'text-blue-500 animate-pulse' : ''} />
                        <span className="italic truncate">"{block.spoken}"</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {/* After-Answer Menu if active */}
            {playerProgress.state === 'after_answer_menu' && playerProgress.currentAnswerId === answer.id && (
              <AfterAnswerMenu
                countdownSeconds={playerProgress.menuCountdownRemaining}
                onRepeat={() => AudioPlayer.playAnswer(answer, activeTab)}
                onNext={() => {
                  AudioPlayer.stopImmediate();
                  onNextAnswer?.();
                }}
                onSimpler={() => {
                  setActiveTab('simple');
                  AudioPlayer.playAnswer(answer, 'simple');
                }}
                onDismiss={() => AudioPlayer.stopImmediate()}
              />
            )}

            {/* Follow-Up Answer Section */}
            {followUpResponse && (
              <div className="p-3 rounded-2xl neu-pressed bg-indigo-50/20 dark:bg-indigo-950/20">
                <div className="flex items-center gap-1.5 text-xs font-bold text-indigo-600 dark:text-indigo-400 mb-1">
                  <Sparkles size={13} />
                  <span>Follow-Up AI Explanation:</span>
                </div>
                <p className="text-xs leading-relaxed text-slate-700 dark:text-slate-300">
                  {followUpResponse}
                </p>
              </div>
            )}
          </div>

          {/* Audio Playback Dock */}
          <div className="mt-4 pt-3 border-t border-slate-300/40 dark:border-slate-700/40 flex flex-col gap-2.5">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <NeuIconButton
                  size="md"
                  variant="raised"
                  onClick={handlePlayToggle}
                  title={isCurrentPlaying ? 'Pause Audio' : 'Listen in Earbuds'}
                >
                  {isCurrentPlaying ? <Pause size={18} /> : <Play size={18} className="text-blue-600 ml-0.5" />}
                </NeuIconButton>

                <div className="flex flex-col">
                  <span className="text-xs font-bold text-slate-800 dark:text-slate-200">
                    {isCurrentPlaying ? 'Playing in Earbuds' : 'Listen Privately'}
                  </span>
                  <span className="text-[10px] text-slate-400">
                    {playerProgress.isMathSlowdownActive ? 'Auto-slowdown active on formula' : 'Desk taps control playback'}
                  </span>
                </div>
              </div>

              {/* Ask Why / Follow-up */}
              <div className="flex items-center gap-1.5 flex-1 max-w-[240px]">
                <input
                  type="text"
                  placeholder="Ask 'Why?' or follow-up..."
                  value={followUpQuery}
                  onChange={e => setFollowUpQuery(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleSendFollowUp()}
                  className="w-full neu-pressed rounded-xl px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100 outline-none"
                />
                <button
                  onClick={handleSendFollowUp}
                  disabled={isAskingFollowUp || !followUpQuery.trim()}
                  className="p-1.5 rounded-xl neu-raised hover:neu-pressed text-blue-600 disabled:opacity-40 cursor-pointer"
                >
                  <ChevronRight size={14} />
                </button>
              </div>
            </div>
          </div>
        </NeuCard>
      </div>
    </div>
  );
};
