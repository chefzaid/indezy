import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TESTIMONIAL_TOKEN_HEADER, TestimonialService } from './testimonial.service';

describe('TestimonialService', () => {
  it('asks, lists, changes and deletes testimonials, and sends the link token in a header', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(TestimonialService);
    const http = TestBed.inject(HttpTestingController);
    service.list().subscribe();
    http.expectOne(call => call.method === 'GET' && call.url.endsWith('/testimonials'));
    service.request({ contactId: 7 }).subscribe();
    expect(http.expectOne(call => call.method === 'POST' && call.url.endsWith('/testimonials')).request.body)
      .toEqual({ contactId: 7 });
    service.setStatus(3, 'APPROVED').subscribe();
    expect(http.expectOne(call => call.url.endsWith('/testimonials/3/status')).request.body).toEqual({ status: 'APPROVED' });
    service.delete(3).subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/testimonials/3'));
    service.open('t').subscribe();
    expect(http.expectOne(call => call.method === 'GET' && call.url.endsWith('/public/testimonials')).request.headers
      .get(TESTIMONIAL_TOKEN_HEADER)).toBe('t');
    service.answer('t', { authorName: 'Léa', text: 'x'.repeat(20), consent: true }).subscribe();
    expect(http.expectOne(call => call.method === 'POST' && call.url.endsWith('/public/testimonials')).request.headers
      .get(TESTIMONIAL_TOKEN_HEADER)).toBe('t');
    http.verify();
  });
});
