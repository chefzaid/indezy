import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AiApplicationPack, AiOfferBrief, AiAvailability, AiChatTurn, AiFit, AiInterview, AiNegotiation, AiPolishTone, AiRateRequest, AiRateSuggestion, AiOfferVerdict, AiMessageDraft, AiMessageKind, AiModel, AiPosting, AiStatus, AiPackChannel, DocumentLanguage } from '../../models';

/** The AI features: whether they can be used, the member's own key, and each feature. */
@Injectable({
  providedIn: 'root'
})
export class AiService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/ai`;

  status(): Observable<AiStatus> {
    return this.http.get<AiStatus>(`${this.API_URL}/status`);
  }

  setKey(apiKey: string, model: AiModel | null): Observable<AiStatus> {
    return this.http.put<AiStatus>(`${this.API_URL}/key`, { apiKey, model });
  }

  removeKey(): Observable<AiStatus> {
    return this.http.delete<AiStatus>(`${this.API_URL}/key`);
  }

  /** The text of a posting saved as a PDF, read on the server without AI, to check before reading it. */
  postingPdfText(file: File): Observable<{ text: string; cut: boolean }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ text: string; cut: boolean }>(`${this.API_URL}/posting/pdf`, form);
  }

  offerBrief(text: string): Observable<AiOfferBrief> {
    return this.http.post<AiOfferBrief>(`${this.API_URL}/offer-brief`, { text });
  }

  readPosting(text: string): Observable<AiPosting> {
    return this.http.post<AiPosting>(`${this.API_URL}/posting`, { text });
  }

  weigh(projectId: number, language: DocumentLanguage, priorities: string | null): Observable<AiFit> {
    return this.http.post<AiFit>(`${this.API_URL}/fit`, { projectId, language, priorities });
  }

  chatHistory(projectId: number): Observable<AiChatTurn[]> {
    return this.http.get<AiChatTurn[]>(`${this.API_URL}/chat/${projectId}`);
  }

  ask(projectId: number, text: string): Observable<AiChatTurn[]> {
    return this.http.post<AiChatTurn[]>(`${this.API_URL}/chat/${projectId}`, { text });
  }

  clearChat(projectId: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/chat/${projectId}`);
  }

  /** The verdict kept on an offer, null when none was asked yet. */
  offerVerdict(offerId: number): Observable<AiOfferVerdict | null> {
    return this.http.get<AiOfferVerdict | null>(`${this.API_URL}/offers/${offerId}/verdict`);
  }

  triageOffer(offerId: number, language: DocumentLanguage): Observable<AiOfferVerdict> {
    return this.http.post<AiOfferVerdict>(`${this.API_URL}/offers/${offerId}/verdict`, null, { params: { language } });
  }

  applicationPack(offerId: number, channel: AiPackChannel, language: DocumentLanguage): Observable<AiApplicationPack> {
    return this.http.post<AiApplicationPack>(`${this.API_URL}/offers/${offerId}/pack`, null, { params: { channel, language } });
  }

  interview(projectId: number, language: DocumentLanguage): Observable<AiInterview> {
    return this.http.post<AiInterview>(`${this.API_URL}/interview/${projectId}`, null, { params: { language } });
  }

  negotiation(projectId: number, language: DocumentLanguage): Observable<AiNegotiation> {
    return this.http.post<AiNegotiation>(`${this.API_URL}/negotiation/${projectId}`, null, { params: { language } });
  }

  /** The announcement of the next availability, drafted: nothing is posted. */
  availability(language: DocumentLanguage): Observable<AiAvailability> {
    return this.http.post<AiAvailability>(`${this.API_URL}/availability`, null, { params: { language } });
  }

  /** The message polished in the tone asked, its {{variables}} kept. */
  polish(subject: string, body: string, tone: AiPolishTone): Observable<AiMessageDraft> {
    return this.http.post<AiMessageDraft>(`${this.API_URL}/polish`, { subject, body, tone });
  }

  suggestRate(request: AiRateRequest): Observable<AiRateSuggestion> {
    return this.http.post<AiRateSuggestion>(`${this.API_URL}/rate`, request);
  }

  /** The dossier de compétences tailored to an opportunity, as a PDF. */
  tailoredDossier(projectId: number, layout: 'NAMED' | 'ANONYMOUS', language: DocumentLanguage): Observable<Blob> {
    return this.http.post(`${this.API_URL}/dossier/${projectId}`, null, { params: { layout, language }, responseType: 'blob' });
  }

  draftMessage(projectId: number, kind: AiMessageKind, language: DocumentLanguage, notes: string | null): Observable<AiMessageDraft> {
    return this.http.post<AiMessageDraft>(`${this.API_URL}/message`, { projectId, kind, language, notes });
  }
}

/** The translation key telling why an AI request failed, from the server's message. */
export function aiErrorKey(error: HttpErrorResponse): string {
  return aiMessageKey(error.error?.message ?? '');
}

/** As {@link aiErrorKey} for a request answered with a file, whose error body comes as a blob. */
export async function aiBlobErrorKey(error: unknown): Promise<string> {
  const body = (error as HttpErrorResponse | null)?.error;
  if (!(body instanceof Blob)) {
    return aiMessageKey((body as { message?: string } | null)?.message ?? '');
  }
  try {
    return aiMessageKey((JSON.parse(await body.text()) as { message?: string }).message ?? '');
  } catch {
    return aiMessageKey('');
  }
}

function aiMessageKey(message: string): string {
  if (message.startsWith('AI is not available')) {
    return 'ai.errors.unavailable';
  }
  if (message.startsWith('Your AI allowance')) {
    return 'ai.errors.accountLimit';
  }
  if (message.startsWith("Indezy's AI allowance")) {
    return 'ai.errors.monthlyLimit';
  }
  if (message.startsWith('The AI declined')) {
    return 'ai.errors.declined';
  }
  if (message.startsWith('The AI key was refused')) {
    return 'ai.errors.keyRefused';
  }
  if (message.startsWith("The AI changed the message's variables")) {
    return 'ai.errors.variables';
  }
  if (message.startsWith('The AI answer was cut short')) {
    return 'ai.errors.tooLong';
  }
  return 'ai.errors.failed';
}
