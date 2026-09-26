import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SourceDto, SourceType } from '../../models/source.models';

@Injectable({
  providedIn: 'root'
})
export class SourceService {
  private readonly API_URL = `${environment.apiUrl}/sources`;

  constructor(private readonly http: HttpClient) {}

  getById(id: number): Observable<SourceDto> {
    return this.http.get<SourceDto>(`${this.API_URL}/${id}`);
  }

  getByFreelanceId(freelanceId: number): Observable<SourceDto[]> {
    return this.http.get<SourceDto[]>(`${this.API_URL}/by-freelance/${freelanceId}`);
  }

  create(source: SourceDto): Observable<SourceDto> {
    return this.http.post<SourceDto>(this.API_URL, source);
  }

  update(id: number, source: SourceDto): Observable<SourceDto> {
    return this.http.put<SourceDto>(`${this.API_URL}/${id}`, source);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  getSourceTypes(): SourceType[] {
    return ['JOB_BOARD', 'SOCIAL_MEDIA', 'EMAIL', 'CALL', 'SMS'];
  }

}
