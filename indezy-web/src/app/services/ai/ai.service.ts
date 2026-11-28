import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { SILENT_HTTP_ERRORS } from '../../interceptors/error.interceptor';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AiAutopilotQueue, AiCvApproved, AiCvReviewed, AiCvRewrite, AiCvStyle, AiSearchHit, AiApplicationPack, AiProfileImport, AiOfferBrief, AiAvailability, AiProspectionMessage, AiChatTurn, AiDebrief, AiFit, AiInterview, AiNegotiation, AiPolishTone, AiRateRequest, AiRateSuggestion, AiOfferVerdict, AiMessageDraft, AiMessageKind, AiModel, AiPosting, AiTask, AiStatus, AiPackChannel, DocumentLanguage, InboxWatcher } from '../../models';

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

  /** The offers the daily autopilot run triaged in the last day, worth applying to first, and whether it is on. */
  autopilot(): Observable<AiAutopilotQueue> {
    return this.http.get<AiAutopilotQueue>(`${this.API_URL}/autopilot`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }

  /** Sets aside an application the autopilot prepared. */
  dismissPack(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/autopilot/packs/${id}`);
  }

  /** Sets aside a follow-up the autopilot drafted. */
  dismissDraft(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/autopilot/drafts/${id}`);
  }

  /** The member's inbox watcher address and the emails forwarded to it lately. */
  inboxWatcher(): Observable<InboxWatcher> {
    return this.http.get<InboxWatcher>(`${this.API_URL}/inbox-watcher`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }

  /** A new inbox watcher address; the previous one receives nothing from then on. */
  renewInboxAddress(): Observable<InboxWatcher> {
    return this.http.post<InboxWatcher>(`${this.API_URL}/inbox-watcher/address`, null);
  }

  setKey(apiKey: string, model: AiModel | null): Observable<AiStatus> {
    return this.http.put<AiStatus>(`${this.API_URL}/key`, { apiKey, model });
  }

  /** The models the member's own key uses for some tasks; a task left out keeps the key's model or its own. */
  setTaskModels(models: Partial<Record<AiTask, AiModel>>): Observable<AiStatus> {
    return this.http.put<AiStatus>(`${this.API_URL}/key/models`, { models });
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

  /** A CV (PDF, Word or OpenDocument) read into the profile's fields with AI, nothing saved. */
  profileImport(file: File): Observable<AiProfileImport> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<AiProfileImport>(`${this.API_URL}/profile-import`, form);
  }

  /** The opportunities and notes that answer a question by its meaning, ten at most, each with why. */
  search(q: string): Observable<AiSearchHit[]> {
    return this.http.post<AiSearchHit[]>(`${this.API_URL}/search`, { q });
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

  /** What comes of an interview of the opportunity, read from the debrief written after it. */
  debrief(projectId: number, notes: string, language: DocumentLanguage): Observable<AiDebrief> {
    return this.http.post<AiDebrief>(`${this.API_URL}/interview/${projectId}/debrief`, { notes }, { params: { language } });
  }

  interview(projectId: number, language: DocumentLanguage): Observable<AiInterview> {
    return this.http.post<AiInterview>(`${this.API_URL}/interview/${projectId}`, null, { params: { language } });
  }

  negotiation(projectId: number, language: DocumentLanguage): Observable<AiNegotiation> {
    return this.http.post<AiNegotiation>(`${this.API_URL}/negotiation/${projectId}`, null, { params: { language } });
  }

  /** The announcement of the next availability, drafted: nothing is posted. */
  /** Each contact's own prospection message from the shared one, 20 contacts at most; nothing sent. */
  prospectionMessages(subject: string, body: string, contactIds: number[], language: DocumentLanguage): Observable<AiProspectionMessage[]> {
    return this.http.post<AiProspectionMessage[]>(`${this.API_URL}/prospection/messages`, { subject, body, contactIds, language });
  }

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

  /** The whole CV rewritten for a positioning and a style, tied to no opportunity. */
  rewriteCv(positioning: string, style: AiCvStyle, language: DocumentLanguage): Observable<AiCvRewrite> {
    return this.http.post<AiCvRewrite>(`${this.API_URL}/cv/rewrite`, { positioning, style, language });
  }

  /** The dossier de compétences with the reviewed wording, as a PDF; no AI is asked. */
  rewrittenCv(reviewed: AiCvReviewed, layout: 'NAMED' | 'ANONYMOUS', language: DocumentLanguage): Observable<Blob> {
    return this.http.post(`${this.API_URL}/cv/pdf`, reviewed, { params: { layout, language }, responseType: 'blob' });
  }

  /** Writes the approved headline, summary and past positions' descriptions to the profile. */
  approveCv(approved: AiCvApproved): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/cv/profile`, approved);
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
