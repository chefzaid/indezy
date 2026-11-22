import { Injectable, NgZone, inject } from '@angular/core';

/** The browser's speech recognition, as Chrome, Edge and Safari name it. */
interface Recognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: { resultIndex: number; results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }> }) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}

type RecognitionConstructor = new () => Recognition;

/** What a dictation tells as it goes: the words heard for good, the ones still being heard, and its end. */
export interface DictationHandlers {
  final(text: string): void;
  interim(text: string): void;
  ended(error: string | null): void;
}

/**
 * Dictation through the browser's own speech recognition (see docs/features.md#project-journal): nothing is recorded or sent to
 * Indezy; the browser turns speech into text, Chrome and Edge through their maker's speech service. Firefox has none.
 */
@Injectable({ providedIn: 'root' })
export class DictationService {
  private readonly zone = inject(NgZone);
  private current: Recognition | null = null;

  private constructorOf(): RecognitionConstructor | null {
    const scope = globalThis as unknown as { SpeechRecognition?: RecognitionConstructor; webkitSpeechRecognition?: RecognitionConstructor };
    return scope.SpeechRecognition ?? scope.webkitSpeechRecognition ?? null;
  }

  supported(): boolean {
    return this.constructorOf() !== null;
  }

  listening(): boolean {
    return this.current !== null;
  }

  /** Starts listening in {@code language} (fr or en); false when the browser has no speech recognition. */
  start(language: string, handlers: DictationHandlers): boolean {
    const Recognizer = this.constructorOf();
    if (!Recognizer) {
      return false;
    }
    this.stop();
    const recognition = new Recognizer();
    recognition.lang = language === 'en' ? 'en-US' : 'fr-FR';
    recognition.continuous = true;
    recognition.interimResults = true;
    let error: string | null = null;
    recognition.onresult = (event): void => this.zone.run(() => {
      let interim = '';
      for (let index = event.resultIndex; index < event.results.length; index++) {
        const result = event.results[index];
        if (result.isFinal) {
          handlers.final(result[0].transcript.trim());
        } else {
          interim += result[0].transcript;
        }
      }
      handlers.interim(interim.trim());
    });
    recognition.onerror = (event): void => {
      error = event.error;
    };
    recognition.onend = (): void => this.zone.run(() => {
      if (this.current === recognition) {
        this.current = null;
      }
      handlers.ended(error);
    });
    this.current = recognition;
    recognition.start();
    return true;
  }

  stop(): void {
    const recognition = this.current;
    this.current = null;
    recognition?.stop();
  }
}
