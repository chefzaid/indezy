import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProjectDto } from '../../models/project.models';

export interface ProjectFilters {
  minRate?: number;
  maxRate?: number;
  workMode?: 'REMOTE' | 'ONSITE' | 'HYBRID';
  startDateAfter?: string;
  techStack?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ProjectService {
  private readonly API_URL = `${environment.apiUrl}/projects`;

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<ProjectDto[]> {
    return this.http.get<ProjectDto[]>(this.API_URL);
  }

  getById(id: number): Observable<ProjectDto> {
    return this.http.get<ProjectDto>(`${this.API_URL}/${id}`);
  }

  getByIdWithSteps(id: number): Observable<ProjectDto> {
    return this.http.get<ProjectDto>(`${this.API_URL}/${id}/with-steps`);
  }

  getByFreelanceId(freelanceId: number): Observable<ProjectDto[]> {
    return this.http.get<ProjectDto[]>(`${this.API_URL}/by-freelance/${freelanceId}`);
  }

  getByClientId(clientId: number): Observable<ProjectDto[]> {
    return this.http.get<ProjectDto[]>(`${this.API_URL}/by-client/${clientId}`);
  }

  getByFreelanceIdWithFilters(freelanceId: number, filters: ProjectFilters): Observable<ProjectDto[]> {
    let params = new HttpParams();
    
    if (filters.minRate !== undefined) {
      params = params.set('minRate', filters.minRate.toString());
    }
    if (filters.maxRate !== undefined) {
      params = params.set('maxRate', filters.maxRate.toString());
    }
    if (filters.workMode) {
      params = params.set('workMode', filters.workMode);
    }
    if (filters.startDateAfter) {
      params = params.set('startDateAfter', filters.startDateAfter);
    }
    if (filters.techStack) {
      params = params.set('techStack', filters.techStack);
    }

    return this.http.get<ProjectDto[]>(`${this.API_URL}/by-freelance/${freelanceId}/filtered`, { params });
  }

  create(project: ProjectDto): Observable<ProjectDto> {
    return this.http.post<ProjectDto>(this.API_URL, project);
  }

  update(id: number, project: ProjectDto): Observable<ProjectDto> {
    return this.http.put<ProjectDto>(`${this.API_URL}/${id}`, project);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  getAverageDailyRate(freelanceId: number): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/stats/average-rate/${freelanceId}`);
  }

  getProjectCount(freelanceId: number): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/stats/count/${freelanceId}`);
  }
}
