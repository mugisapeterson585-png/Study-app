import React, { useState, useMemo } from 'react';
import { 
  AnswerItem, 
  Subject 
} from '../types';
import { 
  Search, 
  Star, 
  Trash2, 
  Play, 
  Volume2, 
  Download, 
  Filter, 
  FileText, 
  RotateCcw, 
  ChevronRight,
  BookOpen
} from 'lucide-react';
import { NeuCard } from '../components/neumorphic/NeuCard';
import { NeuButton } from '../components/neumorphic/NeuButton';
import { NeuTextField } from '../components/neumorphic/NeuTextField';
import { NeuIconButton } from '../components/neumorphic/NeuIconButton';
import { MathRenderer } from '../components/MathRenderer';
import { AppStorage } from '../data/storage';

interface AnswersViewProps {
  answers: AnswerItem[];
  onSelectAnswer: (answer: AnswerItem) => void;
  onPlayAnswer: (answer: AnswerItem) => void;
  onToggleStar: (id: number) => void;
  onDeleteAnswer: (id: number) => void;
  onUndoDelete: () => void;
  deletedItem: AnswerItem | null;
}

const ALL_SUBJECTS = ['All', 'Mathematics', 'Physics', 'Chemistry', 'Biology', 'History', 'General'];

export const AnswersView: React.FC<AnswersViewProps> = ({
  answers,
  onSelectAnswer,
  onPlayAnswer,
  onToggleStar,
  onDeleteAnswer,
  onUndoDelete,
  deletedItem,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSubject, setSelectedSubject] = useState('All');
  const [filterStarredOnly, setFilterStarredOnly] = useState(false);

  // Filtered and searched answers
  const filteredAnswers = useMemo(() => {
    return answers.filter(item => {
      if (filterStarredOnly && !item.starred) return false;
      if (selectedSubject !== 'All' && item.subject !== selectedSubject) return false;
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        const matchesQuestion = item.questionText.toLowerCase().includes(query);
        const matchesAnswer = item.shortAnswer.toLowerCase().includes(query);
        const matchesSubject = item.subject.toLowerCase().includes(query);
        const matchesNumber = item.id.toString() === query.replace('#', '');
        return matchesQuestion || matchesAnswer || matchesSubject || matchesNumber;
      }
      return true;
    });
  }, [answers, searchQuery, selectedSubject, filterStarredOnly]);

  const handlePrintPdf = () => {
    window.print();
  };

  return (
    <div className="max-w-4xl mx-auto px-3 py-4 flex flex-col gap-4 pb-24">
      {/* Search and Quick Filters */}
      <div className="flex flex-col sm:flex-row items-center gap-3">
        <div className="w-full sm:flex-1">
          <NeuTextField
            placeholder="Search answers, subjects, or #number..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            startIcon={<Search size={16} />}
          />
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
          <NeuIconButton
            size="md"
            active={filterStarredOnly}
            onClick={() => setFilterStarredOnly(!filterStarredOnly)}
            title="Show Starred Only"
          >
            <Star size={16} className={filterStarredOnly ? 'fill-amber-400 text-amber-500' : ''} />
          </NeuIconButton>

          <NeuIconButton
            size="md"
            onClick={handlePrintPdf}
            title="Export Revision Sheet / PDF"
          >
            <Download size={16} />
          </NeuIconButton>
        </div>
      </div>

      {/* Horizontal Subject Pill Selectors */}
      <div className="flex gap-1.5 overflow-x-auto py-1 no-scrollbar">
        {ALL_SUBJECTS.map(subject => (
          <button
            key={subject}
            onClick={() => setSelectedSubject(subject)}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-all cursor-pointer ${
              selectedSubject === subject
                ? 'neu-pressed text-blue-600 dark:text-blue-400 font-bold'
                : 'neu-raised text-slate-600 dark:text-slate-300 hover:text-slate-900'
            }`}
          >
            {subject}
          </button>
        ))}
      </div>

      {/* Undo Delete 10-Second Toast */}
      {deletedItem && (
        <div className="p-3 rounded-2xl neu-raised bg-amber-50/50 dark:bg-amber-950/40 border border-amber-500/30 flex items-center justify-between text-xs animate-in fade-in duration-200">
          <span className="text-amber-800 dark:text-amber-200 font-medium">
            Answer #{deletedItem.id} moved to trash (Permanent ID preserved)
          </span>
          <button
            onClick={onUndoDelete}
            className="flex items-center gap-1 font-bold text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
          >
            <RotateCcw size={13} />
            <span>Undo</span>
          </button>
        </div>
      )}

      {/* Empty State */}
      {filteredAnswers.length === 0 && (
        <NeuCard elevation="raised" rounded="3xl" className="p-8 text-center flex flex-col items-center">
          <BookOpen size={40} className="text-slate-400 mb-2 opacity-60" />
          <h3 className="text-sm font-bold text-slate-700 dark:text-slate-200 mb-1">
            No study answers found
          </h3>
          <p className="text-xs text-slate-400 max-w-sm mb-4">
            {searchQuery
              ? 'Try adjusting your search terms or subject filters.'
              : 'Capture your first textbook question or homework problem to start your numbered library!'}
          </p>
        </NeuCard>
      )}

      {/* Answers Grid / List */}
      <div className="space-y-3">
        {filteredAnswers.map(item => (
          <NeuCard
            key={item.id}
            elevation="raised"
            rounded="2xl"
            className="p-4 hover:neu-raised-lg transition-all flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 group"
          >
            <div
              onClick={() => onSelectAnswer(item)}
              className="flex-1 cursor-pointer w-full"
            >
              <div className="flex items-center gap-2 mb-1.5">
                <span className="font-mono font-bold text-sm text-blue-600 dark:text-blue-400 neu-pressed px-2 py-0.5 rounded-lg">
                  #{item.id}
                </span>
                <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">
                  {item.subject}
                </span>
                <span className="text-slate-300 dark:text-slate-600" aria-hidden="true">·</span>
                <span className="text-[11px] text-slate-400 font-mono">
                  {item.blocks?.length || 1} {item.blocks?.length === 1 ? 'block' : 'steps'}
                </span>
              </div>

              <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100 line-clamp-2 mb-1">
                {item.questionText}
              </h4>

              <p className="text-xs text-slate-500 dark:text-slate-400 line-clamp-2 leading-relaxed">
                {item.shortAnswer}
              </p>
            </div>

            {/* Action Bar */}
            <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
              <NeuIconButton
                size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  onPlayAnswer(item);
                }}
                title="Listen in Earbuds"
              >
                <Play size={14} className="text-blue-600 dark:text-blue-400 ml-0.5" />
              </NeuIconButton>

              <NeuIconButton
                size="sm"
                active={item.starred}
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleStar(item.id);
                }}
                title="Star"
              >
                <Star size={14} className={item.starred ? 'fill-amber-400 text-amber-500' : ''} />
              </NeuIconButton>

              <NeuIconButton
                size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  onDeleteAnswer(item.id);
                }}
                title="Delete"
              >
                <Trash2 size={14} className="text-red-500/70 hover:text-red-600" />
              </NeuIconButton>

              <div
                onClick={() => onSelectAnswer(item)}
                className="p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer hidden sm:block"
              >
                <ChevronRight size={18} />
              </div>
            </div>
          </NeuCard>
        ))}
      </div>
    </div>
  );
};
