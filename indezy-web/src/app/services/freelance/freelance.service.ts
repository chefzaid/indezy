import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FreelanceDto } from '../../models/freelance.models';

@Injectable({
  providedIn: 'root'
})
export class FreelanceService {
  private readonly API_URL = `${environment.apiUrl}/freelances`;

  constructor(private readonly http: HttpClient) {}

  getById(id: number): Observable<FreelanceDto> {
    return this.http.get<FreelanceDto>(`${this.API_URL}/${id}`);
  }

  update(id: number, freelance: FreelanceDto): Observable<FreelanceDto> {
    return this.http.put<FreelanceDto>(`${this.API_URL}/${id}`, freelance);
  }

}
