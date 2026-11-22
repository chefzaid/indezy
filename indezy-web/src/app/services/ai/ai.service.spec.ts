import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { AiService, aiErrorKey } from './ai.service';

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
    service.draftMessage(4, 'FOLLOW_UP', 'FR', null).subscribe();
    expect(http.expectOne(`${url}/message`).request.body).toEqual({ projectId: 4, kind: 'FOLLOW_UP', language: 'FR', notes: null });
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
    expect(aiErrorKey(failed())).toBe('ai.errors.failed');
  });
});
