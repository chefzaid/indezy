import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PublicTestimonialRequest, Testimonial, TestimonialAnswer, TestimonialLink, TestimonialRequest, TestimonialStatus } from '../../models';

export const TESTIMONIAL_TOKEN_HEADER = 'X-Testimonial-Token';

/** Testimonials: asked of contacts, written through a link without an account, then shown or hidden. */
@Injectable({ providedIn: 'root' })
export class TestimonialService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/testimonials`;
  private readonly publicUrl = `${environment.apiUrl}/public/testimonials`;

  list(): Observable<Testimonial[]> {
    return this.http.get<Testimonial[]>(this.url);
  }

  /** Asks a contact, or an external person, the link emailed when an address is known. */
  request(request: TestimonialRequest): Observable<TestimonialLink> {
    return this.http.post<TestimonialLink>(this.url, request);
  }

  setStatus(id: number, status: Extract<TestimonialStatus, 'APPROVED' | 'HIDDEN'>): Observable<Testimonial> {
    return this.http.put<Testimonial>(`${this.url}/${id}/status`, { status });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** What the link shows its author; the token comes from the part of the link after #. */
  open(token: string): Observable<PublicTestimonialRequest> {
    return this.http.get<PublicTestimonialRequest>(this.publicUrl, { headers: new HttpHeaders({ [TESTIMONIAL_TOKEN_HEADER]: token }) });
  }

  answer(token: string, answer: TestimonialAnswer): Observable<PublicTestimonialRequest> {
    return this.http.post<PublicTestimonialRequest>(this.publicUrl, answer, {
      headers: new HttpHeaders({ [TESTIMONIAL_TOKEN_HEADER]: token })
    });
  }
}
