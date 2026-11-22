import { TestBed } from '@angular/core/testing';
import { DictationService } from './dictation.service';

class FakeRecognition {
  static last: FakeRecognition | null = null;
  lang = '';
  continuous = false;
  interimResults = false;
  onresult: ((event: unknown) => void) | null = null;
  onerror: ((event: { error: string }) => void) | null = null;
  onend: (() => void) | null = null;
  started = false;
  stopped = false;

  constructor() {
    FakeRecognition.last = this;
  }

  start(): void {
    this.started = true;
  }

  stop(): void {
    this.stopped = true;
    this.onend?.();
  }
}

describe('DictationService', () => {
  const scope = globalThis as unknown as Record<string, unknown>;
  let saved: unknown;
  let savedWebkit: unknown;

  beforeEach(() => {
    saved = scope['SpeechRecognition'];
    savedWebkit = scope['webkitSpeechRecognition'];
  });

  afterEach(() => {
    scope['SpeechRecognition'] = saved;
    scope['webkitSpeechRecognition'] = savedWebkit;
  });

  it('turns speech into final and interim text, in the language asked, until stopped', () => {
    scope['SpeechRecognition'] = undefined;
    scope['webkitSpeechRecognition'] = FakeRecognition;
    const service = TestBed.inject(DictationService);
    expect(service.supported()).toBeTrue();
    const finals: string[] = [];
    const interims: string[] = [];
    let ended: string | null | undefined;
    expect(service.start('fr', { final: text => finals.push(text), interim: text => interims.push(text), ended: error => ended = error })).toBeTrue();
    const recognition = FakeRecognition.last!;
    expect(recognition.lang).toBe('fr-FR');
    expect(recognition.continuous).toBeTrue();
    expect(service.listening()).toBeTrue();

    recognition.onresult!({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: ' Rappeler Léa ' } }, { isFinal: false, 0: { transcript: 'lundi' } }] });
    expect(finals).toEqual(['Rappeler Léa']);
    expect(interims).toEqual(['lundi']);
    recognition.onerror!({ error: 'network' });
    service.stop();
    expect(recognition.stopped).toBeTrue();
    expect(ended).toBe('network');
    expect(service.listening()).toBeFalse();
  });

  it('says when the browser has no speech recognition', () => {
    scope['SpeechRecognition'] = undefined;
    scope['webkitSpeechRecognition'] = undefined;
    const service = TestBed.inject(DictationService);
    expect(service.supported()).toBeFalse();
    expect(service.start('en', { final: () => undefined, interim: () => undefined, ended: () => undefined })).toBeFalse();
    service.stop();
  });
});
