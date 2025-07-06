import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClientDto, ClientStatus, CreateClientDto, UpdateClientDto } from '../../models/client.models';

@Injectable({
  providedIn: 'root'
})
export class ClientService {
  private readonly API_URL = `${environment.apiUrl}/clients`;

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<ClientDto[]> {
    return this.http.get<ClientDto[]>(this.API_URL);
  }

  getById(id: number): Observable<ClientDto> {
    return this.http.get<ClientDto>(`${this.API_URL}/${id}`);
  }

  getByIdWithProjects(id: number): Observable<ClientDto> {
    return this.http.get<ClientDto>(`${this.API_URL}/${id}/with-projects`);
  }

  getByFreelanceId(freelanceId: number): Observable<ClientDto[]> {
    return this.http.get<ClientDto[]>(`${this.API_URL}/by-freelance/${freelanceId}`);
  }

  create(client: CreateClientDto): Observable<ClientDto> {
    return this.http.post<ClientDto>(this.API_URL, client);
  }

  update(id: number, client: UpdateClientDto): Observable<ClientDto> {
    return this.http.put<ClientDto>(`${this.API_URL}/${id}`, client);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  // Legacy methods for backward compatibility
  getClients(): Observable<ClientDto[]> {
    return this.getAll();
  }

  getClient(id: number): Observable<ClientDto> {
    return this.getById(id);
  }

  createClient(clientData: CreateClientDto): Observable<ClientDto> {
    return this.create(clientData);
  }

  updateClient(clientData: UpdateClientDto): Observable<ClientDto> {
    return this.update(clientData.id, clientData);
  }

  deleteClient(id: number): Observable<void> {
    return this.delete(id);
  }

  searchClients(freelanceId: number, query: string): Observable<ClientDto[]> {
    const params = new HttpParams()
      .set('freelanceId', freelanceId.toString())
      .set('query', query);
    return this.http.get<ClientDto[]>(`${this.API_URL}/search`, { params });
  }

  getClientsByStatus(freelanceId: number, status: ClientStatus): Observable<ClientDto[]> {
    const params = new HttpParams()
      .set('freelanceId', freelanceId.toString())
      .set('status', status);
    return this.http.get<ClientDto[]>(`${this.API_URL}/by-status`, { params });
  }

  getClientStats(freelanceId: number): Observable<{total: number, active: number, inactive: number, prospects: number}> {
    return this.http.get<{total: number, active: number, inactive: number, prospects: number}>(`${this.API_URL}/stats/${freelanceId}`);
  }

  getAverageProjectRating(freelanceId: number): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/stats/average-rating/${freelanceId}`);
  }

  getClientCount(freelanceId: number): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/stats/count/${freelanceId}`);
  }
}
