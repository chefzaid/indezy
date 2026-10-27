import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Booking, BookingConfirmation, BookingPage, BookingRequest, BookingSettings, PublicBookingPage } from '../../models';

/** The booking link: its settings and calls for the freelancer, its free times and booking for a recruiter. */
@Injectable({
  providedIn: 'root'
})
export class BookingService {
  private readonly API_URL = environment.apiUrl;

  constructor(private readonly http: HttpClient) {}

  page(): Observable<BookingPage> {
    return this.http.get<BookingPage>(`${this.API_URL}/users/booking-page`);
  }

  save(settings: BookingSettings): Observable<BookingPage> {
    return this.http.put<BookingPage>(`${this.API_URL}/users/booking-page`, settings);
  }

  renewLink(): Observable<BookingPage> {
    return this.http.post<BookingPage>(`${this.API_URL}/users/booking-page/new-link`, null);
  }

  list(): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${this.API_URL}/bookings`);
  }

  cancel(id: number): Observable<Booking> {
    return this.http.post<Booking>(`${this.API_URL}/bookings/${id}/cancel`, null);
  }

  publicPage(slug: string): Observable<PublicBookingPage> {
    return this.http.get<PublicBookingPage>(`${this.API_URL}/public/booking/${encodeURIComponent(slug)}`);
  }

  book(slug: string, request: BookingRequest): Observable<BookingConfirmation> {
    return this.http.post<BookingConfirmation>(`${this.API_URL}/public/booking/${encodeURIComponent(slug)}`, request);
  }
}
