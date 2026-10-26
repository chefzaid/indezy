import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ContactDto, ContactInteraction, ContactInteractions } from '../../models/contact.models';
import { AuthService } from '../auth/auth.service';
import { BulkDeleteResult } from '../../models/bulk.models';

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

  /** Deletes the picked contacts, with their exchanges. */
  deleteAll(ids: number[]): Observable<BulkDeleteResult> {
    return this.http.post<BulkDeleteResult>(`${this.API_URL}/bulk-delete`, { ids });
  }

  /** Imports contacts from a CSV or vCard payload under the given client. */
  importForClient(clientId: number, content: string): Observable<ContactImportResult> {
    return this.http.post<ContactImportResult>(`${this.API_URL}/import/by-client/${clientId}`, { content });
  }

  /** The contact's exchanges, newest first, with its last contact, response rate and preferred channel. */
  getInteractions(contactId: number): Observable<ContactInteractions> {
    return this.http.get<ContactInteractions>(`${this.API_URL}/${contactId}/interactions`);
  }

  addInteraction(contactId: number, interaction: ContactInteraction): Observable<ContactInteraction> {
    return this.http.post<ContactInteraction>(`${this.API_URL}/${contactId}/interactions`, interaction);
  }

  updateInteraction(contactId: number, id: number, interaction: ContactInteraction): Observable<ContactInteraction> {
    return this.http.put<ContactInteraction>(`${this.API_URL}/${contactId}/interactions/${id}`, interaction);
  }

  deleteInteraction(contactId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${contactId}/interactions/${id}`);
  }
}
