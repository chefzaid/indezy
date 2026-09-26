import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ContactDto } from '../../models/contact.models';
import { AuthService } from '../auth/auth.service';

export interface ContactImportResult {
  imported: number;
  skipped: number;
  total: number;
}

@Injectable({
  providedIn: 'root'
})
export class ContactService {
  private readonly API_URL = `${environment.apiUrl}/contacts`;

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService
  ) {}

  /** Contacts of the signed-in freelance; screens must never list other accounts' contacts. */
  getForCurrentFreelance(): Observable<ContactDto[]> {
    const freelanceId = this.authService.getUser()?.id;
    return freelanceId ? this.getByFreelanceId(freelanceId) : of([]);
  }

  getById(id: number): Observable<ContactDto> {
    return this.http.get<ContactDto>(`${this.API_URL}/${id}`);
  }

  getByFreelanceId(freelanceId: number): Observable<ContactDto[]> {
    return this.http.get<ContactDto[]>(`${this.API_URL}/by-freelance/${freelanceId}`);
  }

  getByClientId(clientId: number): Observable<ContactDto[]> {
    return this.http.get<ContactDto[]>(`${this.API_URL}/by-client/${clientId}`);
  }

  create(contact: ContactDto): Observable<ContactDto> {
    return this.http.post<ContactDto>(this.API_URL, contact);
  }

  update(id: number, contact: ContactDto): Observable<ContactDto> {
    return this.http.put<ContactDto>(`${this.API_URL}/${id}`, contact);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  /** Imports contacts from a CSV or vCard payload under the given client. */
  importForClient(clientId: number, content: string): Observable<ContactImportResult> {
    return this.http.post<ContactImportResult>(`${this.API_URL}/import/by-client/${clientId}`, { content });
  }
}
