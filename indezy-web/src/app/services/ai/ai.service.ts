import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AiChatTurn, AiFit, AiOfferVerdict, AiMessageDraft, AiMessageKind, AiModel, AiPosting, AiStatus, DocumentLanguage } from '../../models';

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

  draftMessage(projectId: number, kind: AiMessageKind, language: DocumentLanguage, notes: string | null): Observable<AiMessageDraft> {
    return this.http.post<AiMessageDraft>(`${this.API_URL}/message`, { projectId, kind, language, notes });
  }
}

/** The translation key telling why an AI request failed, from the server's message. */
export function aiErrorKey(error: HttpErrorResponse): string {
  const message: string = error.error?.message ?? '';
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
  if (message.startsWith('The AI answer was cut short')) {
    return 'ai.errors.tooLong';
  }
  return 'ai.errors.failed';
}
