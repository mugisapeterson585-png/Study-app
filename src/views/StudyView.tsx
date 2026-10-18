import React, { useState, useEffect } from 'react';
import { 
  AnswerItem, 
  StudySessionState, 
  AppSettings 
} from '../types';
import { 
  Play, 
  Pause, 
  RotateCcw, 
  Clock, 
  Award, 
  Flame, 
  Zap, 
  Layers, 
  HelpCircle,
  CheckCircle,
  XCircle,
  Sparkles,
  ArrowRight
} from 'lucide-react';
import { NeuCard } from '../components/neumorphic/NeuCard';
import { NeuButton } from '../components/neumorphic/NeuButton';
import { NeuIconButton } from '../components/neumorphic/NeuIconButton';
import { DeskKnockPad } from '../components/DeskKnockPad';
import { AudioPlayer } from '../audio/AudioPlayer';
import { DeskTapEngine } from '../gestures/DeskTapEngine';
import confetti from 'canvas-confetti';

interface StudyViewProps {
  answers: AnswerItem[];
  sessionState: StudySessionState;
  onToggleSession: () => void;
  settings: AppSettings;
  onSelectAnswer: (answer: AnswerItem) => void;
}

export const StudyView: React.FC<StudyViewProps> = ({
  answers,
  sessionState,
  onToggleSession,
  settings,
  onSelectAnswer,
}) => {
  const [activeTab, setActiveTab] = useState<'session' | 'quiz' | 'steps' | 'pomodoro'>('session');
  
  // Quiz state
  const [quizIndex, setQuizIndex] = useState(0);
  const [quizScore, setQuizScore] = useState({ knew: 0, missed: 0 });
  const [isQuizCardFlipped, setIsQuizCardFlipped] = useState(false);

  // Step-by-step solver state
  const [selectedStepAnswer, setSelectedStepAnswer] = useState<AnswerItem | null>(answers[0] || null);
  const [currentStepReveal, setCurrentStepReveal] = useState(1);

  // Pomodoro state
  const [pomoMinutes, setPomoMinutes] = useState(25);
  const [pomoSeconds, setPomoSeconds] = useState(0);
  const [pomoRunning, setPomoRunning] = useState(false);
  const [pomoMode, setPomoMode] = useState<'study' | 'break'>('study');

  // Handle desk taps in quiz mode: 1 tap = knew it, 2 taps = didn't know it
  useEffect(() => {
    if (activeTab !== 'quiz') return;
    const unsub = DeskTapEngine.subscribeGestures((gesture) => {
      if (gesture === '1_tap') {
        handleQuizAnswer(true);
      } else if (gesture === '2_taps') {
        handleQuizAnswer(false);
      }
    });
    return () => unsub();
  }, [activeTab, quizIndex, answers]);

  // Pomodoro timer loop
  useEffect(() => {
    if (!pomoRunning) return;
    const interval = setInterval(() => {
      setPomoSeconds(prev => {
        if (prev === 0) {
          if (pomoMinutes === 0) {
            AudioPlayer.playChime();
            if (pomoMode === 'study') {
              setPomoMode('break');
              setPomoMinutes(settings.pomodoroBreakMinutes || 5);
            } else {
              setPomoMode('study');
              setPomoMinutes(settings.pomodoroMinutes || 25);
            }
            return 0;
          }
          setPomoMinutes(m => m - 1);
          return 59;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [pomoRunning, pomoMinutes, pomoMode, settings]);

  const handleQuizAnswer = (knew: boolean) => {
    if (knew) {
      setQuizScore(s => ({ ...s, knew: s.knew + 1 }));
      AudioPlayer.playGestureTick(1);
    } else {
      setQuizScore(s => ({ ...s, missed: s.missed + 1 }));
      AudioPlayer.playGestureTick(2);
    }

    if (quizIndex < answers.length - 1) {
      setQuizIndex(i => i + 1);
      setIsQuizCardFlipped(false);
    } else {
      confetti({ particleCount: 60, spread: 55, origin: { y: 0.6 } });
    }
  };

  const handleNextStep = () => {
    if (!selectedStepAnswer) return;
    const totalSteps = selectedStepAnswer.blocks?.length || 1;
    if (currentStepReveal < totalSteps) {
      setCurrentStepReveal(c => c + 1);
      AudioPlayer.playGestureTick(1);
    }
  };

  const currentQuizItem = answers[quizIndex] || null;

  return (
    <div className="max-w-4xl mx-auto px-3 py-4 flex flex-col gap-4 pb-24">
      {/* Study Navigation Tabs */}
      <div className="flex items-center gap-1.5 p-1 rounded-2xl neu-pressed">
        <button
          onClick={() => setActiveTab('session')}
          className={`flex-1 py-2 text-xs font-semibold rounded-xl transition-all cursor-pointer ${
            activeTab === 'session' ? 'neu-raised text-blue-600 dark:text-blue-400 font-bold' : 'text-slate-500'
          }`}
        >
          Session
        </button>
        <button
          onClick={() => setActiveTab('quiz')}
          className={`flex-1 py-2 text-xs font-semibold rounded-xl transition-all cursor-pointer ${
            activeTab === 'quiz' ? 'neu-raised text-blue-600 dark:text-blue-400 font-bold' : 'text-slate-500'
          }`}
        >
          Quiz Cards
        </button>
        <button
          onClick={() => setActiveTab('steps')}
          className={`flex-1 py-2 text-xs font-semibold rounded-xl transition-all cursor-pointer ${
            activeTab === 'steps' ? 'neu-raised text-blue-600 dark:text-blue-400 font-bold' : 'text-slate-500'
          }`}
        >
          Step Solver
        </button>
        <button
          onClick={() => setActiveTab('pomodoro')}
          className={`flex-1 py-2 text-xs font-semibold rounded-xl transition-all cursor-pointer ${
            activeTab === 'pomodoro' ? 'neu-raised text-blue-600 dark:text-blue-400 font-bold' : 'text-slate-500'
          }`}
        >
          Pomodoro
        </button>
      </div>

      {/* Tab 1: Live Study Session */}
      {activeTab === 'session' && (
        <div className="flex flex-col gap-4">
          <NeuCard elevation="raised" rounded="3xl" className="p-6 text-center">
            <div className="flex justify-between items-center mb-4">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                Library Study Session
              </span>
              <div className="flex items-center gap-1.5 px-3 py-1 rounded-full neu-pressed text-xs font-semibold text-blue-600 dark:text-blue-400">
                <Flame size={14} className="text-amber-500" />
                <span>{sessionState.isActive ? 'Active Session' : 'Ready'}</span>
              </div>
            </div>

            {/* Big Tactile Clock */}
            <div className="my-6">
              <span className="text-6xl font-bold font-mono tracking-tight text-slate-800 dark:text-slate-100">
                {Math.floor(sessionState.durationSeconds / 60).toString().padStart(2, '0')}:
                {(sessionState.durationSeconds % 60).toString().padStart(2, '0')}
              </span>
              <span className="block text-xs text-slate-400 mt-2">
                Screen-off desk tap gestures active while resting flat
              </span>
            </div>

            {/* Session Stats */}
            <div className="grid grid-cols-2 gap-3 max-w-sm mx-auto mb-6">
              <div className="p-3 rounded-2xl neu-pressed text-center">
                <span className="text-lg font-bold text-slate-800 dark:text-slate-200">
                  {sessionState.answersHeard}
                </span>
                <span className="block text-[11px] text-slate-400">Answers Heard</span>
              </div>
              <div className="p-3 rounded-2xl neu-pressed text-center">
                <span className="text-lg font-bold text-slate-800 dark:text-slate-200">
                  {answers.length}
                </span>
                <span className="block text-[11px] text-slate-400">Library Total</span>
              </div>
            </div>

            {/* Start / Stop Button */}
            <div className="max-w-xs mx-auto">
              <NeuButton
                variant={sessionState.isActive ? 'raised' : 'accent'}
                size="lg"
                fullWidth
                onClick={onToggleSession}
              >
                {sessionState.isActive ? (
                  <div className="flex items-center gap-2 text-red-500">
                    <Pause size={18} />
                    <span>End Study Session</span>
                  </div>
                ) : (
                  <div className="flex items-center gap-2">
                    <Play size={18} />
                    <span>Start Study Session</span>
                  </div>
                )}
              </NeuButton>
            </div>
          </NeuCard>

          {/* Interactive Desk Knock Pad */}
          <DeskKnockPad lastGesture={null} />
        </div>
      )}

      {/* Tab 2: Quiz Mode with Desk Taps */}
      {activeTab === 'quiz' && (
        <div className="flex flex-col gap-4">
          <NeuCard elevation="raised" rounded="2xl" className="p-4 flex items-center justify-between">
            <div>
              <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Audio Flashcard Quiz
              </span>
              <p className="text-xs text-slate-400">
                Answer silently: 1 tap = Knew it · 2 taps = Missed it
              </p>
            </div>
            <div className="flex items-center gap-2 font-mono text-xs">
              <span className="text-emerald-600 font-bold">✓ {quizScore.knew}</span>
              <span className="text-slate-300">/</span>
              <span className="text-red-500 font-bold">✗ {quizScore.missed}</span>
            </div>
          </NeuCard>

          {currentQuizItem ? (
            <NeuCard elevation="raised-lg" rounded="3xl" className="p-6 text-center">
              <div className="flex justify-between items-center mb-3">
                <span className="px-2.5 py-1 rounded-xl neu-pressed text-xs font-mono font-bold text-blue-600">
                  #{currentQuizItem.id} · {currentQuizItem.subject}
                </span>
                <span className="text-xs text-slate-400">
                  Question {quizIndex + 1} of {answers.length}
                </span>
              </div>

              <h3 className="text-base font-bold text-slate-800 dark:text-slate-100 my-4 leading-relaxed">
                {currentQuizItem.questionText}
              </h3>

              {isQuizCardFlipped ? (
                <div className="p-4 rounded-2xl neu-pressed bg-blue-50/20 dark:bg-blue-950/20 my-4 text-left">
                  <span className="text-[11px] font-bold text-blue-600 uppercase block mb-1">
                    Answer & Explanation:
                  </span>
                  <p className="text-xs text-slate-700 dark:text-slate-300 leading-relaxed">
                    {currentQuizItem.shortAnswer}
                  </p>
                </div>
              ) : (
                <button
                  onClick={() => setIsQuizCardFlipped(true)}
                  className="px-4 py-2 rounded-xl neu-raised text-xs font-semibold text-blue-600 hover:neu-pressed my-4 cursor-pointer"
                >
                  Reveal Answer
                </button>
              )}

              {/* Action buttons or desk knock inputs */}
              <div className="grid grid-cols-2 gap-3 max-w-sm mx-auto mt-4">
                <NeuButton
                  size="md"
                  onClick={() => handleQuizAnswer(true)}
                  className="text-emerald-600 hover:text-emerald-700 font-bold"
                >
                  <CheckCircle size={16} className="mr-1.5" />
                  <span>Knew It (1 Tap)</span>
                </NeuButton>

                <NeuButton
                  size="md"
                  onClick={() => handleQuizAnswer(false)}
                  className="text-red-500 hover:text-red-600 font-bold"
                >
                  <XCircle size={16} className="mr-1.5" />
                  <span>Missed (2 Taps)</span>
                </NeuButton>
              </div>
            </NeuCard>
          ) : (
            <NeuCard elevation="raised" rounded="2xl" className="p-6 text-center">
              <p className="text-xs text-slate-400">No questions available for quiz.</p>
            </NeuCard>
          )}

          <DeskKnockPad lastGesture={null} />
        </div>
      )}

      {/* Tab 3: Step-by-Step Solver */}
      {activeTab === 'steps' && (
        <div className="flex flex-col gap-4">
          <NeuCard elevation="raised" rounded="2xl" className="p-4">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500 block mb-1">
              Step-by-Step Solver
            </span>
            <p className="text-xs text-slate-400 mb-3">
              Solves equations in gradual stages, pausing and waiting for a desk tap before revealing each next step.
            </p>

            {/* Answer Selector */}
            <div className="flex gap-2 overflow-x-auto py-1">
              {answers.map(ans => (
                <button
                  key={ans.id}
                  onClick={() => {
                    setSelectedStepAnswer(ans);
                    setCurrentStepReveal(1);
                  }}
                  className={`px-3 py-1.5 rounded-xl text-xs font-semibold shrink-0 cursor-pointer ${
                    selectedStepAnswer?.id === ans.id
                      ? 'neu-pressed text-blue-600 font-bold'
                      : 'neu-raised text-slate-600'
                  }`}
                >
                  #{ans.id} {ans.subject}
                </button>
              ))}
            </div>
          </NeuCard>

          {selectedStepAnswer && (
            <NeuCard elevation="raised-lg" rounded="3xl" className="p-5">
              <div className="mb-4 pb-3 border-b border-slate-300/40 dark:border-slate-700/40">
                <span className="text-xs font-bold text-blue-600">
                  Problem #{selectedStepAnswer.id}:
                </span>
                <p className="text-sm font-semibold text-slate-800 dark:text-slate-100 mt-1">
                  {selectedStepAnswer.questionText}
                </p>
              </div>

              {/* Revealed Steps */}
              <div className="space-y-3">
                {selectedStepAnswer.blocks?.slice(0, currentStepReveal).map((block, idx) => (
                  <div key={idx} className="p-3 rounded-2xl neu-pressed animate-in fade-in duration-300">
                    <span className="text-[10px] font-mono font-bold text-blue-500 block mb-1">
                      {block.stepNumber ? `Step ${block.stepNumber}` : `Block ${idx + 1}`}
                    </span>
                    <p className="text-xs text-slate-800 dark:text-slate-200">
                      {block.display}
                    </p>
                  </div>
                ))}
              </div>

              {/* Next Step Trigger */}
              <div className="mt-5 flex justify-between items-center">
                <span className="text-xs text-slate-400">
                  Step {currentStepReveal} of {selectedStepAnswer.blocks?.length || 1}
                </span>

                {currentStepReveal < (selectedStepAnswer.blocks?.length || 1) ? (
                  <NeuButton variant="accent" size="md" onClick={handleNextStep}>
                    <span>Tap Desk for Next Step</span>
                    <ArrowRight size={14} className="ml-1.5" />
                  </NeuButton>
                ) : (
                  <span className="text-xs font-bold text-emerald-600 flex items-center gap-1">
                    <CheckCircle size={14} />
                    <span>Complete Proof Solution</span>
                  </span>
                )}
              </div>
            </NeuCard>
          )}

          <DeskKnockPad lastGesture={null} />
        </div>
      )}

      {/* Tab 4: Pomodoro Focus Timer */}
      {activeTab === 'pomodoro' && (
        <div className="flex flex-col gap-4">
          <NeuCard elevation="raised" rounded="3xl" className="p-6 text-center">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Gentle Earbud Pomodoro (25/5 Cycle)
            </span>

            <div className="my-8">
              <span className="text-6xl font-bold font-mono tracking-tight text-slate-800 dark:text-slate-100">
                {pomoMinutes.toString().padStart(2, '0')}:{pomoSeconds.toString().padStart(2, '0')}
              </span>
              <span className="block text-xs font-semibold text-blue-600 uppercase tracking-widest mt-2">
                {pomoMode === 'study' ? 'Deep Study Interval' : 'Rest Break Interval'}
              </span>
            </div>

            <div className="flex justify-center gap-3">
              <NeuButton
                variant={pomoRunning ? 'raised' : 'accent'}
                size="lg"
                onClick={() => setPomoRunning(!pomoRunning)}
              >
                {pomoRunning ? <Pause size={18} className="mr-1.5" /> : <Play size={18} className="mr-1.5" />}
                <span>{pomoRunning ? 'Pause Focus' : 'Start Focus'}</span>
              </NeuButton>

              <NeuIconButton
                size="lg"
                onClick={() => {
                  setPomoRunning(false);
                  setPomoMinutes(25);
                  setPomoSeconds(0);
                  setPomoMode('study');
                }}
                title="Reset Timer"
              >
                <RotateCcw size={18} />
              </NeuIconButton>
            </div>
          </NeuCard>
        </div>
      )}
    </div>
  );
};
