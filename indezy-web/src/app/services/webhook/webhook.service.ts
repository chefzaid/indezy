import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreatedWebhook, Webhook, WebhookDelivery, WebhookWrite } from '../../models';

/** The workspace's webhooks: written, tested, and their latest deliveries. */
@Injectable({
  providedIn: 'root'
})
export class WebhookService {
  private readonly API_URL = `${environment.apiUrl}/users/webhooks`;

  constructor(private readonly http: HttpClient) {}

  list(): Observable<Webhook[]> {
    return this.http.get<Webhook[]>(this.API_URL);
  }

  create(write: WebhookWrite): Observable<CreatedWebhook> {
    return this.http.post<CreatedWebhook>(this.API_URL, write);
  }

  update(id: number, write: WebhookWrite): Observable<Webhook> {
    return this.http.put<Webhook>(`${this.API_URL}/${id}`, write);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  ping(id: number): Observable<WebhookDelivery> {
    return this.http.post<WebhookDelivery>(`${this.API_URL}/${id}/ping`, null);
  }

  deliveries(id: number): Observable<WebhookDelivery[]> {
    return this.http.get<WebhookDelivery[]>(`${this.API_URL}/${id}/deliveries`);
  }
}
