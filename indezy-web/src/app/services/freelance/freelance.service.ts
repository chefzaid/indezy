import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FreelanceDto } from '../../models/freelance.models';

@Injectable({
  providedIn: 'root'
})
export class FreelanceService {
  private readonly API_URL = `${environment.apiUrl}/freelances`;

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<FreelanceDto[]> {
    return this.http.get<FreelanceDto[]>(this.API_URL);
  }

  getById(id: number): Observable<FreelanceDto> {
    return this.http.get<FreelanceDto>(`${this.API_URL}/${id}`);
  }

  getByIdWithProjects(id: number): Observable<FreelanceDto> {
    return this.http.get<FreelanceDto>(`${this.API_URL}/${id}/with-projects`);
  }

  getByEmail(email: string): Observable<FreelanceDto> {
    const params = new HttpParams().set('email', email);
    return this.http.get<FreelanceDto>(`${this.API_URL}/by-email`, { params });
  }

  create(freelance: FreelanceDto): Observable<FreelanceDto> {
    return this.http.post<FreelanceDto>(this.API_URL, freelance);
  }

  update(id: number, freelance: FreelanceDto): Observable<FreelanceDto> {
    return this.http.put<FreelanceDto>(`${this.API_URL}/${id}`, freelance);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  checkEmailExists(email: string): Observable<boolean> {
    const params = new HttpParams().set('email', email);
    return this.http.get<boolean>(`${this.API_URL}/exists`, { params });
  }

  updatePassword(id: number, newPassword: string): Observable<void> {
    return this.http.patch<void>(`${this.API_URL}/${id}/password`, newPassword);
  }
}
