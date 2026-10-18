import React, { useState, useRef, useEffect } from 'react';
import { 
  Camera, 
  FlipHorizontal, 
  Timer, 
  Sparkles, 
  Trash2, 
  Plus, 
  Sliders, 
  Upload, 
  Check, 
  Loader2,
  FileText,
  Eye,
  ArrowRight
} from 'lucide-react';
import { NeuButton } from './neumorphic/NeuButton';
import { NeuIconButton } from './neumorphic/NeuIconButton';
import { NeuCard } from './neumorphic/NeuCard';
import { NeuToggle } from './neumorphic/NeuToggle';
import { NeuSlider } from './neumorphic/NeuSlider';
import { AppSettings } from '../types';

interface CameraScannerProps {
  settings: AppSettings;
  onPhotosReady: (photos: string[], promptNote: string) => void;
  isAnalyzing: boolean;
}

// Built-in high quality textbook & handwritten exam question templates for immediate testing
const SAMPLE_EXAM_PRESETS = [
  {
    title: 'Maths: Complex Integral',
    url: 'https://images.unsplash.com/photo-1635070041078-e363dbe005cb?w=600&auto=format&fit=crop&q=80',
    note: 'Calculate the definite integral and show step-by-step substitution.'
  },
  {
    title: 'Physics: Kinematics Circuit',
    url: 'https://images.unsplash.com/photo-1509228468518-180dd4864904?w=600&auto=format&fit=crop&q=80',
    note: 'Find the current flowing through resistor R2.'
  },
  {
    title: 'Biology: Cell Membrane Diagram',
    url: 'https://images.unsplash.com/photo-1532094349884-543bc11b234d?w=600&auto=format&fit=crop&q=80',
    note: 'Identify transport mechanisms across the phospholipid bilayer.'
  }
];

export const CameraScanner: React.FC<CameraScannerProps> = ({
  settings,
  onPhotosReady,
  isAnalyzing,
}) => {
  const [capturedPhotos, setCapturedPhotos] = useState<string[]>([]);
  const [isCameraActive, setIsCameraActive] = useState<boolean>(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [countdown, setCountdown] = useState<number | null>(null);
  const [isTimerMode, setIsTimerMode] = useState<boolean>(false);
  const [autoEnhance, setAutoEnhance] = useState<boolean>(settings.autoEnhance);
  const [showEnhanceEditor, setShowEnhanceEditor] = useState<boolean>(false);
  const [contrastBoost, setContrastBoost] = useState<number>(30);
  const [brightnessBoost, setBrightnessBoost] = useState<number>(10);
  const [promptNote, setPromptNote] = useState<string>('');
  const [selectedPhotoIndex, setSelectedPhotoIndex] = useState<number>(0);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Initialize camera stream
  const startCamera = async () => {
    try {
      setCameraError(null);
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(t => t.stop());
      }
      const constraints: MediaStreamConstraints = {
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1920 },
          height: { ideal: 1080 }
        }
      };
      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        videoRef.current.play();
      }
      setIsCameraActive(true);
    } catch (err: any) {
      console.warn('Camera access error:', err);
      setCameraError('Camera unavailable or permission denied. You can upload photos or select sample exam questions below.');
      setIsCameraActive(false);
    }
  };

  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(t => t.stop());
      streamRef.current = null;
    }
    setIsCameraActive(false);
  };

  useEffect(() => {
    startCamera();
    return () => stopCamera();
  }, []);

  // Listen to volume keys or spacebar for shutter
  useEffect(() => {
    if (!settings.volumeShutterEnabled) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === ' ' || e.key === 'VolumeUp' || e.key === 'VolumeDown') {
        if (isCameraActive && !isAnalyzing && countdown === null) {
          e.preventDefault();
          handleShutterClick();
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isCameraActive, isAnalyzing, countdown, settings.volumeShutterEnabled, isTimerMode]);

  // Capture current video frame into canvas and apply auto-crop/contrast enhancement
  const captureFrame = () => {
    if (!videoRef.current) return;
    const video = videoRef.current;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    if (autoEnhance) {
      // Document enhancement filter (contrast + slight brightness)
      ctx.filter = `contrast(${100 + contrastBoost}%) brightness(${100 + brightnessBoost}%)`;
    }

    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const dataUrl = canvas.toDataURL('image/jpeg', 0.88);
    setCapturedPhotos(prev => [...prev, dataUrl]);
    setSelectedPhotoIndex(capturedPhotos.length);
  };

  const handleShutterClick = () => {
    if (isTimerMode) {
      setCountdown(settings.timerLengthSec || 3);
      const timer = setInterval(() => {
        setCountdown(prev => {
          if (prev === null || prev <= 1) {
            clearInterval(timer);
            captureFrame();
            return null;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      captureFrame();
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    Array.from(files).forEach(file => {
      const reader = new FileReader();
      reader.onload = (loadEvent) => {
        if (loadEvent.target?.result) {
          setCapturedPhotos(prev => [...prev, loadEvent.target!.result as string]);
        }
      };
      reader.readAsDataURL(file);
    });
  };

  const removePhoto = (idx: number) => {
    setCapturedPhotos(prev => prev.filter((_, i) => i !== idx));
    if (selectedPhotoIndex >= idx && selectedPhotoIndex > 0) {
      setSelectedPhotoIndex(selectedPhotoIndex - 1);
    }
  };

  const handleSendToAi = () => {
    if (capturedPhotos.length === 0) return;
    onPhotosReady(capturedPhotos, promptNote);
  };

  const selectPreset = (preset: typeof SAMPLE_EXAM_PRESETS[0]) => {
    setCapturedPhotos([preset.url]);
    setPromptNote(preset.note);
  };

  return (
    <div className="flex flex-col gap-4 pb-24">
      {/* Viewfinder Card */}
      <NeuCard elevation="raised" rounded="3xl" className="overflow-hidden p-3 relative">
        <div className="relative w-full aspect-[4/3] sm:aspect-[16/10] bg-slate-900 rounded-2xl overflow-hidden flex items-center justify-center">
          {isCameraActive ? (
            <video
              ref={videoRef}
              playsInline
              muted
              className={`w-full h-full object-cover transition-all ${
                autoEnhance ? 'filter contrast-[115%] brightness-[105%]' : ''
              }`}
            />
          ) : (
            <div className="p-6 text-center text-slate-400 flex flex-col items-center max-w-sm">
              <Camera size={40} className="mb-2 text-slate-500 opacity-60" />
              <p className="text-xs leading-relaxed mb-3">
                {cameraError || 'Camera feed paused.'}
              </p>
              <NeuButton size="sm" onClick={startCamera}>
                Retry Camera
              </NeuButton>
            </div>
          )}

          {/* Document Framing Reticle */}
          <div className="absolute inset-4 border border-dashed border-white/30 rounded-xl pointer-events-none flex flex-col justify-between p-2">
            <div className="flex justify-between items-center text-[10px] text-white/70 font-mono tracking-wider">
              <span>[ DOCUMENT BOUNDS ]</span>
              {autoEnhance && <span className="text-blue-300">AUTO-ENHANCE ON</span>}
            </div>
            <div className="text-center text-[10px] text-white/60">
              Align question, equations, or diagrams
            </div>
          </div>

          {/* Countdown Overlay */}
          {countdown !== null && (
            <div className="absolute inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-20">
              <span className="text-7xl font-bold font-mono text-white animate-ping">
                {countdown}
              </span>
            </div>
          )}

          {/* Viewfinder Toolbar */}
          <div className="absolute top-3 left-3 right-3 flex justify-between items-center z-10 pointer-events-auto">
            <button
              onClick={() => setIsTimerMode(!isTimerMode)}
              className={`p-2 rounded-xl backdrop-blur-md transition-all cursor-pointer ${
                isTimerMode ? 'bg-blue-600 text-white' : 'bg-black/40 text-white/80 hover:bg-black/60'
              }`}
              title="3-Second Timer Mode (Allows propping phone on desk)"
            >
              <Timer size={16} />
            </button>

            <button
              onClick={() => setAutoEnhance(!autoEnhance)}
              className={`px-2.5 py-1.5 rounded-xl backdrop-blur-md text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
                autoEnhance ? 'bg-blue-600 text-white' : 'bg-black/40 text-white/80 hover:bg-black/60'
              }`}
              title="Auto Document Scanner Filter (Contrast & clarity boost)"
            >
              <Sparkles size={13} />
              <span>Enhance</span>
            </button>
          </div>
        </div>

        {/* Shutter Bar */}
        <div className="mt-4 flex items-center justify-between px-3">
          <input
            type="file"
            ref={fileInputRef}
            accept="image/*"
            multiple
            onChange={handleFileUpload}
            className="hidden"
          />
          <NeuIconButton
            size="md"
            onClick={() => fileInputRef.current?.click()}
            title="Upload exam photo or page scan"
          >
            <Upload size={18} />
          </NeuIconButton>

          {/* Big Tactile Shutter Button */}
          <button
            onClick={handleShutterClick}
            disabled={isAnalyzing}
            className="w-18 h-18 rounded-full neu-raised active:neu-pressed flex items-center justify-center p-2 cursor-pointer transition-transform active:scale-95 group"
            title="Take Photo (or press Spacebar / Volume key)"
          >
            <div className="w-13 h-13 rounded-full bg-blue-600 group-hover:bg-blue-500 shadow-lg shadow-blue-500/40 flex items-center justify-center transition-all">
              <Camera size={24} className="text-white" />
            </div>
          </button>

          <NeuIconButton
            size="md"
            active={showEnhanceEditor}
            onClick={() => setShowEnhanceEditor(!showEnhanceEditor)}
            title="Adjust brightness & contrast"
          >
            <Sliders size={18} />
          </NeuIconButton>
        </div>

        {/* Enhancement Controls Slider Panel */}
        {showEnhanceEditor && (
          <div className="mt-3 p-3 rounded-2xl neu-pressed flex flex-col gap-2">
            <NeuSlider
              label="Contrast Boost (Text Sharpness)"
              min={0}
              max={60}
              value={contrastBoost}
              onChange={setContrastBoost}
              unit="%"
            />
            <NeuSlider
              label="Brightness Boost (Page Paper)"
              min={-20}
              max={30}
              value={brightnessBoost}
              onChange={setBrightnessBoost}
              unit="%"
            />
          </div>
        )}
      </NeuCard>

      {/* Multi-Photo Thumbnails Tray */}
      {capturedPhotos.length > 0 && (
        <NeuCard elevation="raised" rounded="2xl" className="p-3">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <FileText size={15} className="text-blue-500" />
              <span className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                Multi-Photo Question ({capturedPhotos.length} {capturedPhotos.length === 1 ? 'page' : 'pages'})
              </span>
            </div>
            <button
              onClick={() => fileInputRef.current?.click()}
              className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1 cursor-pointer"
            >
              <Plus size={13} />
              <span>Add Page</span>
            </button>
          </div>

          <div className="flex gap-2.5 overflow-x-auto py-1">
            {capturedPhotos.map((photo, idx) => (
              <div
                key={idx}
                className="relative group shrink-0 w-20 h-24 rounded-xl overflow-hidden neu-pressed border border-slate-300/40 dark:border-slate-700/40"
              >
                <img
                  src={photo}
                  alt={`Question page ${idx + 1}`}
                  className="w-full h-full object-cover"
                />
                <div className="absolute top-1 left-1 px-1.5 py-0.5 rounded-md bg-black/60 text-[9px] font-mono text-white">
                  #{idx + 1}
                </div>
                <button
                  onClick={() => removePhoto(idx)}
                  className="absolute top-1 right-1 p-1 rounded-md bg-red-600/90 text-white opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                >
                  <Trash2 size={11} />
                </button>
              </div>
            ))}
          </div>

          {/* Optional prompt note / focus request */}
          <div className="mt-3">
            <input
              type="text"
              placeholder="Optional hint: e.g. 'Solve part b only' or 'Explain formula'"
              value={promptNote}
              onChange={e => setPromptNote(e.target.value)}
              className="w-full neu-pressed rounded-xl px-3 py-2 text-xs text-slate-800 dark:text-slate-100 outline-none"
            />
          </div>

          {/* Primary Action Button: Send to AI */}
          <div className="mt-3">
            <NeuButton
              variant="accent"
              fullWidth
              size="lg"
              disabled={isAnalyzing}
              onClick={handleSendToAi}
            >
              {isAnalyzing ? (
                <div className="flex items-center gap-2">
                  <Loader2 size={18} className="animate-spin" />
                  <span>Analyzing question with AI...</span>
                </div>
              ) : (
                <div className="flex items-center justify-center gap-2">
                  <span>Send to AI & Get Numbered Answer</span>
                  <ArrowRight size={16} />
                </div>
              )}
            </NeuButton>
          </div>
        </NeuCard>
      )}

      {/* Preset Sample Exam Questions for instant testing */}
      {capturedPhotos.length === 0 && (
        <NeuCard elevation="raised-sm" rounded="2xl" className="p-3">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-600 dark:text-slate-300 block mb-2">
            Or Test With Sample Exam Questions:
          </span>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
            {SAMPLE_EXAM_PRESETS.map((preset, idx) => (
              <button
                key={idx}
                onClick={() => selectPreset(preset)}
                className="p-2.5 rounded-xl neu-raised hover:neu-pressed text-left transition-all cursor-pointer flex flex-col justify-between"
              >
                <span className="text-xs font-bold text-slate-800 dark:text-slate-100">
                  {preset.title}
                </span>
                <span className="text-[10px] text-slate-500 dark:text-slate-400 line-clamp-1 mt-1">
                  {preset.note}
                </span>
              </button>
            ))}
          </div>
        </NeuCard>
      )}
    </div>
  );
};
