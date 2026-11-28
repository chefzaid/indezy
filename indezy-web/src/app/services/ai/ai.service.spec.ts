import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { AiService, aiBlobErrorKey, aiErrorKey } from './ai.service';

describe('AiService', () => {
  it('reads the status, sets and removes the own key, reads a posting and drafts a message', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(AiService);
    const http = TestBed.inject(HttpTestingController);
    const url = `${environment.apiUrl}/ai`;
    service.status().subscribe();
    http.expectOne(`${url}/status`).flush({});
    service.setKey('sk-ant-x', 'claude-haiku-5-5').subscribe();
    const key = http.expectOne(`${url}/key`);
    expect(key.request.method).toBe('PUT');
    expect(key.request.body).toEqual({ apiKey: 'sk-ant-x', model: 'claude-haiku-5-5' });
    key.flush({});
    service.removeKey().subscribe();
    expect(http.expectOne(`${url}/key`).request.method).toBe('DELETE');
    service.readPosting('Mission Java').subscribe();
    expect(http.expectOne(`${url}/posting`).request.body).toEqual({ text: 'Mission Java' });
    service.weigh(4, 'EN', 'Remote first').subscribe();
    expect(http.expectOne(`${url}/fit`).request.body).toEqual({ projectId: 4, language: 'EN', priorities: 'Remote first' });
    service.chatHistory(4).subscribe();
    http.expectOne(`${url}/chat/4`).flush([]);
    service.ask(4, 'Comment préparer ?').subscribe();
    expect(http.expectOne(`${url}/chat/4`).request.body).toEqual({ text: 'Comment préparer ?' });
    service.clearChat(4).subscribe();
    expect(http.expectOne(`${url}/chat/4`).request.method).toBe('DELETE');
    service.offerVerdict(9).subscribe(verdict => expect(verdict).toBeNull());
    http.expectOne(`${url}/offers/9/verdict`).flush(null, { status: 204, statusText: 'No Content' });
    service.triageOffer(9, 'FR').subscribe();
    expect(http.expectOne(`${url}/offers/9/verdict?language=FR`).request.method).toBe('POST');
    service.draftMessage(4, 'FOLLOW_UP', 'FR', null).subscribe();
    expect(http.expectOne(`${url}/message`).request.body).toEqual({ projectId: 4, kind: 'FOLLOW_UP', language: 'FR', notes: null });
    service.postingPdfText(new File(['%PDF'], 'a.pdf')).subscribe();
    const pdf = http.expectOne(`${url}/posting/pdf`);
    expect(pdf.request.body instanceof FormData).toBeTrue();
    pdf.flush({ text: 'x', cut: false });
    service.profileImport(new File(['%PDF'], 'cv.pdf')).subscribe();
    const cv = http.expectOne(`${url}/profile-import`);
    expect(cv.request.body instanceof FormData).toBeTrue();
    cv.flush({ languages: [], skills: [] });
    service.suggestRate({ role: 'Lead', techStack: null, workMode: null, remoteDaysPerMonth: null, place: null, durationInMonths: null,
      description: null, language: 'FR' }).subscribe();
    expect(http.expectOne(`${url}/rate`).request.body.role).toBe('Lead');
    service.availability('EN').subscribe();
    expect(http.expectOne(`${url}/availability?language=EN`).request.method).toBe('POST');
    service.polish('point', 'bonjour', 'FORMAL').subscribe();
    expect(http.expectOne(`${url}/polish`).request.body).toEqual({ subject: 'point', body: 'bonjour', tone: 'FORMAL' });
    service.reviewDossier(4, 'EN').subscribe();
    expect(http.expectOne(`${url}/dossier/4/review?language=EN`).request.method).toBe('POST');
    service.reviewOfferDossier(5, 'FR').subscribe();
    expect(http.expectOne(`${url}/offers/5/dossier/review?language=FR`).request.method).toBe('POST');
    service.reviewPostingDossier('Mission Kafka', 'FR').subscribe();
    expect(http.expectOne(`${url}/dossier/posting/review?language=FR`).request.body).toEqual({ text: 'Mission Kafka' });
    service.refineCv({ text: 'Paie.', refinement: 'SHORTER', focus: null, locked: ['Paie'], language: 'FR' }).subscribe();
    expect(http.expectOne(`${url}/cv/refine`).request.body.locked).toEqual(['Paie']);
    http.verify();
  });

  it('tells why a request failed from the server\'s message', () => {
    const failed = (message?: string) => new HttpErrorResponse({ status: 409, error: message ? { message } : null });
    expect(aiErrorKey(failed('AI is not available yet'))).toBe('ai.errors.unavailable');
    expect(aiErrorKey(failed('Your AI allowance for this month is used up: add your own key to go on'))).toBe('ai.errors.accountLimit');
    expect(aiErrorKey(failed("Indezy's AI allowance for this month is used up: add your own key to go on"))).toBe('ai.errors.monthlyLimit');
    expect(aiErrorKey(failed('The AI declined this request'))).toBe('ai.errors.declined');
    expect(aiErrorKey(failed('The AI key was refused: check it'))).toBe('ai.errors.keyRefused');
    expect(aiErrorKey(failed('The AI answer was cut short: shorten the text'))).toBe('ai.errors.tooLong');
    expect(aiErrorKey(failed("The AI changed the message's variables: polish it again"))).toBe('ai.errors.variables');
    expect(aiErrorKey(failed('The AI changed a locked phrase: refine the block again or edit it by hand'))).toBe('ai.errors.locked');
    expect(aiErrorKey(failed())).toBe('ai.errors.failed');
  });

  it('tells why a file request failed from the message in its blob body', async () => {
    const blob = (text: string) => new HttpErrorResponse({ status: 409, error: new Blob([text], { type: 'application/json' }) });
    expect(await aiBlobErrorKey(blob('{"message":"Your AI allowance for this month is used up"}'))).toBe('ai.errors.accountLimit');
    expect(await aiBlobErrorKey(blob('not json'))).toBe('ai.errors.failed');
    expect(await aiBlobErrorKey(new HttpErrorResponse({ status: 409, error: { message: 'The AI declined this request' } })))
      .toBe('ai.errors.declined');
    expect(await aiBlobErrorKey(new Error('down'))).toBe('ai.errors.failed');
    expect(await aiBlobErrorKey(null)).toBe('ai.errors.failed');
  });
});
