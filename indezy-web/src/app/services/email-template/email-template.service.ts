import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EmailTemplate } from '../../models';

/** The workspace's own email templates. */
@Injectable({
  providedIn: 'root'
})
export class EmailTemplateService {
  private readonly API_URL = `${environment.apiUrl}/email-templates`;

  constructor(private readonly http: HttpClient) {}

  list(): Observable<EmailTemplate[]> {
    return this.http.get<EmailTemplate[]>(this.API_URL);
  }

  create(template: EmailTemplate): Observable<EmailTemplate> {
    return this.http.post<EmailTemplate>(this.API_URL, template);
  }

  update(id: number, template: EmailTemplate): Observable<EmailTemplate> {
    return this.http.put<EmailTemplate>(`${this.API_URL}/${id}`, template);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
