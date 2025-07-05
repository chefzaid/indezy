import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface FreelanceDto {
  id?: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  city?: string;
  status: 'AVAILABLE' | 'EMPLOYED' | 'UNAVAILABLE';
  noticePeriodInDays?: number;
  availabilityDate?: string;
  reversionRate?: number;
  cvFilePath?: string;
  fullName?: string;
  totalProjects?: number;
  averageDailyRate?: number;
}

@Injectable({
  providedIn: 'root'
})
export class FreelanceService {
  private readonly API_URL = `${environment.apiUrl}/freelances`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<FreelanceDto[]> {
    return this.http.get<FreelanceDto[]>(this.API_URL);
  }

  getById(id: number): Observable<FreelanceDto> {
    return this.http.get<FreelanceDto>(`${this.API_URL}/${id}`);
  }

  getByIdWithProjects(id: number): Observable<FreelanceDto> {
    // Mock data for development - replace with real API call when backend is ready
    return new Observable(observer => {
      setTimeout(() => {
        const mockFreelance: FreelanceDto = {
          id: id,
          firstName: 'John',
          lastName: 'Doe',
          email: 'john.doe@email.com',
          phone: '+33 6 12 34 56 78',
          address: '123 Rue de la Paix, 75001 Paris',
          status: 'AVAILABLE',
          totalProjects: 3,
          averageDailyRate: 633
        };
        observer.next(mockFreelance);
        observer.complete();
      }, 500);
    });
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
