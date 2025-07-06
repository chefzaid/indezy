import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SourceDto, SourceType } from '../../models/source.models';

export interface SourceFilters {
  type?: SourceType;
  minPopularityRating?: number;
  maxPopularityRating?: number;
  minUsefulnessRating?: number;
  maxUsefulnessRating?: number;
  isListing?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class SourceService {
  private readonly API_URL = `${environment.apiUrl}/sources`;

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<SourceDto[]> {
    return this.http.get<SourceDto[]>(this.API_URL);
  }

  getById(id: number): Observable<SourceDto> {
    return this.http.get<SourceDto>(`${this.API_URL}/${id}`);
  }

  getByIdWithProjects(id: number): Observable<SourceDto> {
    return this.http.get<SourceDto>(`${this.API_URL}/${id}/with-projects`);
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

  getAverageRatings(freelanceId: number): Observable<{averagePopularityRating: number, averageUsefulnessRating: number}> {
    return this.http.get<{averagePopularityRating: number, averageUsefulnessRating: number}>(`${this.API_URL}/by-freelance/${freelanceId}/average-ratings`);
  }

  getSourceCount(freelanceId: number): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/stats/count/${freelanceId}`);
  }

  searchSources(freelanceId: number, filters: SourceFilters): Observable<SourceDto[]> {
    let params = new HttpParams();
    
    if (filters.type) {
      params = params.set('type', filters.type);
    }
    if (filters.minPopularityRating !== undefined) {
      params = params.set('minPopularityRating', filters.minPopularityRating.toString());
    }
    if (filters.maxPopularityRating !== undefined) {
      params = params.set('maxPopularityRating', filters.maxPopularityRating.toString());
    }
    if (filters.minUsefulnessRating !== undefined) {
      params = params.set('minUsefulnessRating', filters.minUsefulnessRating.toString());
    }
    if (filters.maxUsefulnessRating !== undefined) {
      params = params.set('maxUsefulnessRating', filters.maxUsefulnessRating.toString());
    }
    if (filters.isListing !== undefined) {
      params = params.set('isListing', filters.isListing.toString());
    }

    return this.http.get<SourceDto[]>(`${this.API_URL}/by-freelance/${freelanceId}/search`, { params });
  }

  getSourceTypes(): SourceType[] {
    return ['JOB_BOARD', 'SOCIAL_MEDIA', 'EMAIL', 'CALL', 'SMS'];
  }

  getSourceTypeLabel(type: SourceType): string {
    const labels: Record<SourceType, string> = {
      'JOB_BOARD': 'Job Board',
      'SOCIAL_MEDIA': 'Social Media',
      'EMAIL': 'Email',
      'CALL': 'Call',
      'SMS': 'SMS'
    };
    return labels[type] || type;
  }
}
