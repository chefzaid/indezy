import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClientDto, CreateClientDto, UpdateClientDto } from '../../models/client.models';
import { AuthService } from '../auth/auth.service';

@Injectable({
  providedIn: 'root'
})
export class ClientService {
  private readonly API_URL = `${environment.apiUrl}/clients`;
  private readonly authService = inject(AuthService);

  constructor(private readonly http: HttpClient) {}

  /** Clients of the signed-in freelance; screens must never list other accounts' clients. */
  getForCurrentFreelance(): Observable<ClientDto[]> {
    const freelanceId = this.authService.getUser()?.id;
    return freelanceId ? this.getByFreelanceId(freelanceId) : of([]);
  }

  getById(id: number): Observable<ClientDto> {
    return this.http.get<ClientDto>(`${this.API_URL}/${id}`);
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
}
