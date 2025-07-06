import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ContactDto } from '../../models/contact.models';

// Export the interface for use in other components
export { ContactDto } from '../../models/contact.models';

@Injectable({
  providedIn: 'root'
})
export class ContactService {
  private readonly API_URL = `${environment.apiUrl}/contacts`;

  constructor(private readonly http: HttpClient) {}
  getAll(): Observable<ContactDto[]> {
    return this.http.get<ContactDto[]>(this.API_URL);
  }

  getById(id: number): Observable<ContactDto> {
    return this.http.get<ContactDto>(`${this.API_URL}/${id}`);
  }

  getContact(id: number): Observable<ContactDto> {
    return this.getById(id);
  }

  getContacts(): Observable<ContactDto[]> {
    return this.getAll();
  }

  create(contact: ContactDto): Observable<ContactDto> {
    return this.http.post<ContactDto>(this.API_URL, contact);
  }

  createContact(contact: ContactDto): Observable<ContactDto> {
    return this.create(contact);
  }

  update(id: number, contact: ContactDto): Observable<ContactDto> {
    return this.http.put<ContactDto>(`${this.API_URL}/${id}`, contact);
  }

  updateContact(id: number, contact: Partial<ContactDto>): Observable<ContactDto> {
    return this.http.put<ContactDto>(`${this.API_URL}/${id}`, contact);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  deleteContact(id: number): Observable<void> {
    return this.delete(id);
  }

  getByFreelanceId(freelanceId: number): Observable<ContactDto[]> {
    return this.http.get<ContactDto[]>(`${this.API_URL}/by-freelance/${freelanceId}`);
  }

  getByClientId(clientId: number): Observable<ContactDto[]> {
    return this.http.get<ContactDto[]>(`${this.API_URL}/by-client/${clientId}`);
  }

  getContactsByClient(clientId: number): Observable<ContactDto[]> {
    return this.getByClientId(clientId);
  }

  searchByName(freelanceId: number, name: string): Observable<ContactDto[]> {
    const params = new HttpParams().set('name', name);
    return this.http.get<ContactDto[]>(`${this.API_URL}/by-freelance/${freelanceId}/search/name`, { params });
  }

  searchByEmail(freelanceId: number, email: string): Observable<ContactDto[]> {
    const params = new HttpParams().set('email', email);
    return this.http.get<ContactDto[]>(`${this.API_URL}/by-freelance/${freelanceId}/search/email`, { params });
  }

  searchContacts(query: string): Observable<ContactDto[]> {
    // For backward compatibility, search by name for the current freelance
    // This would need to be updated to get the current freelance ID from context
    const freelanceId = 1; // TODO: Get from authentication/context service
    return this.searchByName(freelanceId, query);
  }

  filterContacts(filters: {
    status?: string;
    clientId?: number;
  }): Observable<ContactDto[]> {
    let params = new HttpParams();

    if (filters.status) {
      params = params.set('status', filters.status);
    }

    if (filters.clientId) {
      return this.getByClientId(filters.clientId);
    }

    return this.http.get<ContactDto[]>(this.API_URL, { params });
  }
}
