import { AnswerBlock, AnswerItem, AiProfile, Subject } from '../types';

export interface AnswerRequest {
  images: string[];            // base64 strings or data URLs
  questionPrompt?: string;     // additional instructions / notes
  subjectHint?: string;
  detailMode?: 'short' | 'detailed' | 'simple';
}

export interface AnswerResult {
  subject: string;
  question_text: string;
  short_answer: string;
  blocks: AnswerBlock[];
  simple_explanation: string;
}

export interface ConnectionTestResult {
  success: boolean;
  message: string;
  latencyMs: number;
}

const SYSTEM_INSTRUCTION = `You are SilentStudy, an elite private AI study companion.
The user takes photos of exam questions, textbook pages, math equations, or science diagrams.
You must analyze the image(s) accurately (including messy handwriting, graphs, and tables) and return STRICT JSON with this exact schema:

{
  "subject": "Mathematics" | "Physics" | "Chemistry" | "Biology" | "History" | "Computer Science" | "Literature" | "Economics" | "General",
  "question_text": "Clean transcript of the question from the photo",
  "short_answer": "1-2 sentence TL;DR direct answer",
  "blocks": [
    {
      "type": "text" | "math" | "step",
      "display": "Formatted written form. Use LaTeX for math ($...$ or $$...$$). For steps, number them clearly.",
      "spoken": "CRITICAL: Natural-language reading for earbud text-to-speech. Never say raw symbols like 'x^2' or '\\frac{a}{b}'. Instead write out: 'x squared', 'a over b', 'negative b plus or minus the square root of b squared minus 4 a c, all over 2 a'. For steps, write 'Step 1: ...', 'Step 2: ...'",
      "stepNumber": 1
    }
  ],
  "simple_explanation": "Explain-like-I'm-new conceptual explanation in clear, simple everyday English."
}

Rules:
1. Always break long calculations or proofs into numbered 'step' blocks.
2. For every equation in 'math' or 'step', 'spoken' must be completely verbalized for listening in earbuds while 'display' uses KaTeX LaTeX.
3. Respond ONLY with valid JSON. No conversational markdown, no code fences.`;

// Clean JSON helper
function extractJsonFromText(rawText: string): any {
  let cleaned = rawText.trim();
  if (cleaned.startsWith('```json')) {
    cleaned = cleaned.replace(/^```json\s*/, '').replace(/\s*```$/, '');
  } else if (cleaned.startsWith('```')) {
    cleaned = cleaned.replace(/^```\s*/, '').replace(/\s*```$/, '');
  }
  
  // Find first { and last }
  const firstBrace = cleaned.indexOf('{');
  const lastBrace = cleaned.lastIndexOf('}');
  if (firstBrace !== -1 && lastBrace !== -1 && lastBrace > firstBrace) {
    cleaned = cleaned.substring(firstBrace, lastBrace + 1);
  }
  
  return JSON.parse(cleaned);
}

// Convert data URL or blob to base64 and mime
function parseImageData(dataUrlOrBase64: string): { mimeType: string; base64: string } {
  if (dataUrlOrBase64.startsWith('data:')) {
    const parts = dataUrlOrBase64.split(',');
    const mimeMatch = parts[0].match(/:(.*?);/);
    const mimeType = mimeMatch ? mimeMatch[1] : 'image/jpeg';
    return { mimeType, base64: parts[1] };
  }
  return { mimeType: 'image/jpeg', base64: dataUrlOrBase64 };
}

export class AiProviderService {
  /**
   * Test connection with a small request
   */
  static async testConnection(profile: AiProfile): Promise<ConnectionTestResult> {
    const startTime = performance.now();
    try {
      if (profile.provider === 'gemini') {
        const apiKey = profile.apiKey || (import.meta as any).env?.VITE_GEMINI_API_KEY || (import.meta as any).env?.GEMINI_API_KEY || '';
        if (!apiKey) {
          return { success: false, message: 'No API key provided for Google Gemini.', latencyMs: 0 };
        }
        const model = profile.model || 'gemini-2.5-flash';
        const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
        
        const response = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            contents: [{ parts: [{ text: 'Respond with the single word: OK' }] }]
          })
        });

        if (!response.ok) {
          const err = await response.json().catch(() => ({}));
          throw new Error(err.error?.message || `HTTP ${response.status}: ${response.statusText}`);
        }
      } else if (profile.provider === 'openai') {
        if (!profile.apiKey) {
          return { success: false, message: 'No OpenAI API key provided.', latencyMs: 0 };
        }
        const response = await fetch('https://api.openai.com/v1/chat/completions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${profile.apiKey}`
          },
          body: JSON.stringify({
            model: profile.model || 'gpt-4o-mini',
            messages: [{ role: 'user', content: 'Say OK' }],
            max_tokens: 5
          })
        });
        if (!response.ok) {
          const err = await response.json().catch(() => ({}));
          throw new Error(err.error?.message || `HTTP ${response.status}: ${response.statusText}`);
        }
      } else if (profile.provider === 'claude') {
        if (!profile.apiKey) {
          return { success: false, message: 'No Anthropic API key provided.', latencyMs: 0 };
        }
        const response = await fetch('https://api.anthropic.com/v1/messages', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'x-api-key': profile.apiKey,
            'anthropic-version': '2023-06-01',
            'dangerously-allow-browser': 'true'
          },
          body: JSON.stringify({
            model: profile.model || 'claude-3-5-haiku-20241022',
            max_tokens: 10,
            messages: [{ role: 'user', content: 'Say OK' }]
          })
        });
        if (!response.ok) {
          const err = await response.json().catch(() => ({}));
          throw new Error(err.error?.message || `HTTP ${response.status}: ${response.statusText}`);
        }
      } else if (profile.provider === 'openrouter') {
        if (!profile.apiKey) {
          return { success: false, message: 'No OpenRouter API key provided.', latencyMs: 0 };
        }
        const response = await fetch('https://openrouter.ai/api/v1/chat/completions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${profile.apiKey}`,
            'HTTP-Referer': window.location.origin,
            'X-Title': 'SilentStudy'
          },
          body: JSON.stringify({
            model: profile.model || 'google/gemini-2.0-flash-001',
            messages: [{ role: 'user', content: 'Say OK' }],
            max_tokens: 5
          })
        });
        if (!response.ok) {
          const err = await response.json().catch(() => ({}));
          throw new Error(err.error?.message || `HTTP ${response.status}: ${response.statusText}`);
        }
      } else if (profile.provider === 'custom') {
        const baseUrl = (profile.baseUrl || 'http://localhost:11434/v1').replace(/\/+$/, '');
        const response = await fetch(`${baseUrl}/chat/completions`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(profile.apiKey ? { 'Authorization': `Bearer ${profile.apiKey}` } : {})
          },
          body: JSON.stringify({
            model: profile.model || 'llama3.2-vision',
            messages: [{ role: 'user', content: 'Say OK' }],
            max_tokens: 5
          })
        });
        if (!response.ok) {
          const err = await response.json().catch(() => ({}));
          throw new Error(err.error?.message || `HTTP ${response.status}: ${response.statusText}`);
        }
      }

      const latencyMs = Math.round(performance.now() - startTime);
      return {
        success: true,
        message: `Connected successfully (${latencyMs}ms)`,
        latencyMs
      };
    } catch (error: any) {
      const latencyMs = Math.round(performance.now() - startTime);
      return {
        success: false,
        message: error.message || 'Connection failed. Check key, URL, or CORS settings.',
        latencyMs
      };
    }
  }

  /**
   * Main answer call to the chosen provider
   */
  static async answer(
    profile: AiProfile,
    request: AnswerRequest,
    fallbackProfile?: AiProfile
  ): Promise<AnswerResult> {
    try {
      return await this.executeAnswer(profile, request);
    } catch (primaryError: any) {
      console.warn(`Primary AI provider (${profile.name}) failed:`, primaryError);
      if (fallbackProfile && fallbackProfile.id !== profile.id) {
        console.info(`Attempting fallback to (${fallbackProfile.name})...`);
        return await this.executeAnswer(fallbackProfile, request);
      }
      throw primaryError;
    }
  }

  private static async executeAnswer(profile: AiProfile, request: AnswerRequest): Promise<AnswerResult> {
    if (profile.provider === 'gemini') {
      return await this.callGemini(profile, request);
    } else if (profile.provider === 'openai') {
      return await this.callOpenAI(profile, request);
    } else if (profile.provider === 'claude') {
      return await this.callClaude(profile, request);
    } else if (profile.provider === 'openrouter') {
      return await this.callOpenRouter(profile, request);
    } else if (profile.provider === 'custom') {
      return await this.callCustomOpenAiCompatible(profile, request);
    }
    throw new Error(`Unsupported AI provider: ${profile.provider}`);
  }

  // --- Gemini Adapter ---
  private static async callGemini(profile: AiProfile, request: AnswerRequest): Promise<AnswerResult> {
    const apiKey = profile.apiKey || (import.meta as any).env?.VITE_GEMINI_API_KEY || (import.meta as any).env?.GEMINI_API_KEY || '';
    if (!apiKey) {
      throw new Error('Google Gemini API key is missing. Please set it in Settings > AI.');
    }
    const model = profile.model || 'gemini-2.5-flash';
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

    const parts: any[] = [];
    parts.push({ text: SYSTEM_INSTRUCTION });
    if (request.questionPrompt) {
      parts.push({ text: `User Note: ${request.questionPrompt}` });
    }

    for (const img of request.images) {
      const { mimeType, base64 } = parseImageData(img);
      parts.push({
        inlineData: {
          mimeType,
          data: base64
        }
      });
    }

    parts.push({ text: 'Analyze the question image(s) and provide the complete study companion structured output JSON.' });

    const payload = {
      contents: [{ parts }],
      generationConfig: {
        responseMimeType: 'application/json',
        temperature: profile.temperature ?? 0.2,
        maxOutputTokens: profile.maxTokens ?? 3000
      }
    };

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.error?.message || `Gemini error ${response.status}`);
    }

    const data = await response.json();
    const rawContent = data.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!rawContent) {
      throw new Error('Empty response received from Gemini.');
    }

    return this.validateAndNormalizeAnswer(extractJsonFromText(rawContent));
  }

  // --- OpenAI Adapter ---
  private static async callOpenAI(profile: AiProfile, request: AnswerRequest): Promise<AnswerResult> {
    if (!profile.apiKey) {
      throw new Error('OpenAI API key is missing. Please set it in Settings > AI.');
    }
    const model = profile.model || 'gpt-4o-mini';

    const contentParts: any[] = [
      { type: 'text', text: `${SYSTEM_INSTRUCTION}\n\nAnalyze the attached question image(s).` }
    ];

    if (request.questionPrompt) {
      contentParts.push({ type: 'text', text: `User Note: ${request.questionPrompt}` });
    }

    for (const img of request.images) {
      const formattedUrl = img.startsWith('data:') ? img : `data:image/jpeg;base64,${img}`;
      contentParts.push({
        type: 'image_url',
        image_url: { url: formattedUrl, detail: 'high' }
      });
    }

    const response = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${profile.apiKey}`
      },
      body: JSON.stringify({
        model,
        messages: [{ role: 'user', content: contentParts }],
        response_format: { type: 'json_object' },
        temperature: profile.temperature ?? 0.2,
        max_tokens: profile.maxTokens ?? 3000
      })
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.error?.message || `OpenAI error ${response.status}`);
    }

    const data = await response.json();
    const rawContent = data.choices?.[0]?.message?.content;
    if (!rawContent) throw new Error('No content from OpenAI');

    return this.validateAndNormalizeAnswer(extractJsonFromText(rawContent));
  }

  // --- Claude Adapter ---
  private static async callClaude(profile: AiProfile, request: AnswerRequest): Promise<AnswerResult> {
    if (!profile.apiKey) {
      throw new Error('Anthropic Claude API key is missing. Please set it in Settings > AI.');
    }
    const model = profile.model || 'claude-3-5-sonnet-20241022';

    const contentParts: any[] = [];
    for (const img of request.images) {
      const { mimeType, base64 } = parseImageData(img);
      contentParts.push({
        type: 'image',
        source: {
          type: 'base64',
          media_type: mimeType,
          data: base64
        }
      });
    }

    contentParts.push({
      type: 'text',
      text: `${SYSTEM_INSTRUCTION}\n\n${request.questionPrompt ? `User Note: ${request.questionPrompt}\n` : ''}Provide the structured output in strict JSON format now.`
    });

    const response = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': profile.apiKey,
        'anthropic-version': '2023-06-01',
        'dangerously-allow-browser': 'true'
      },
      body: JSON.stringify({
        model,
        max_tokens: profile.maxTokens ?? 3000,
        temperature: profile.temperature ?? 0.2,
        messages: [{ role: 'user', content: contentParts }]
      })
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.error?.message || `Anthropic error ${response.status}`);
    }

    const data = await response.json();
    const rawContent = data.content?.[0]?.text;
    if (!rawContent) throw new Error('No content from Claude');

    return this.validateAndNormalizeAnswer(extractJsonFromText(rawContent));
  }

  // --- OpenRouter Adapter ---
  private static async callOpenRouter(profile: AiProfile, request: AnswerRequest): Promise<AnswerResult> {
    if (!profile.apiKey) {
      throw new Error('OpenRouter API key is missing. Please set it in Settings > AI.');
    }
    const model = profile.model || 'google/gemini-2.0-flash-001';

    const contentParts: any[] = [
      { type: 'text', text: `${SYSTEM_INSTRUCTION}\n\nAnalyze the question image(s).` }
    ];

    for (const img of request.images) {
      const formattedUrl = img.startsWith('data:') ? img : `data:image/jpeg;base64,${img}`;
      contentParts.push({
        type: 'image_url',
        image_url: { url: formattedUrl }
      });
    }

    const response = await fetch('https://openrouter.ai/api/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${profile.apiKey}`,
        'HTTP-Referer': window.location.origin,
        'X-Title': 'SilentStudy'
      },
      body: JSON.stringify({
        model,
        messages: [{ role: 'user', content: contentParts }],
        response_format: { type: 'json_object' },
        temperature: profile.temperature ?? 0.2,
        max_tokens: profile.maxTokens ?? 3000
      })
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.error?.message || `OpenRouter error ${response.status}`);
    }

    const data = await response.json();
    const rawContent = data.choices?.[0]?.message?.content;
    if (!rawContent) throw new Error('No content from OpenRouter');

    return this.validateAndNormalizeAnswer(extractJsonFromText(rawContent));
  }

  // --- Custom / OpenAI Compatible Adapter ---
  private static async callCustomOpenAiCompatible(profile: AiProfile, request: AnswerRequest): Promise<AnswerResult> {
    const baseUrl = (profile.baseUrl || 'http://localhost:11434/v1').replace(/\/+$/, '');
    const model = profile.model || 'llama3.2-vision';

    const contentParts: any[] = [
      { type: 'text', text: `${SYSTEM_INSTRUCTION}\n\nAnalyze the question.` }
    ];

    for (const img of request.images) {
      const formattedUrl = img.startsWith('data:') ? img : `data:image/jpeg;base64,${img}`;
      contentParts.push({
        type: 'image_url',
        image_url: { url: formattedUrl }
      });
    }

    const response = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(profile.apiKey ? { 'Authorization': `Bearer ${profile.apiKey}` } : {})
      },
      body: JSON.stringify({
        model,
        messages: [{ role: 'user', content: contentParts }],
        temperature: profile.temperature ?? 0.2,
        max_tokens: profile.maxTokens ?? 3000
      })
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.error?.message || `Custom server error ${response.status}`);
    }

    const data = await response.json();
    const rawContent = data.choices?.[0]?.message?.content;
    if (!rawContent) throw new Error('No response from custom endpoint');

    return this.validateAndNormalizeAnswer(extractJsonFromText(rawContent));
  }

  /**
   * Validates and normalizes structured output
   */
  private static validateAndNormalizeAnswer(raw: any): AnswerResult {
    const subject = raw.subject || 'General';
    const question_text = raw.question_text || 'Untitled Question';
    const short_answer = raw.short_answer || 'No short summary provided.';
    const simple_explanation = raw.simple_explanation || short_answer;

    let blocks: AnswerBlock[] = [];
    if (Array.isArray(raw.blocks) && raw.blocks.length > 0) {
      blocks = raw.blocks.map((b: any, idx: number) => ({
        type: (b.type === 'math' || b.type === 'step' || b.type === 'text') ? b.type : 'text',
        display: b.display || b.text || '',
        spoken: b.spoken || b.display || b.text || '',
        stepNumber: b.stepNumber || (b.type === 'step' ? idx + 1 : undefined)
      }));
    } else {
      blocks = [
        {
          type: 'text',
          display: short_answer,
          spoken: short_answer
        }
      ];
    }

    return {
      subject,
      question_text,
      short_answer,
      blocks,
      simple_explanation
    };
  }
}
