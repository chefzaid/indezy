import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  InterviewStepDto,
  CreateInterviewStepDto,
  UpdateInterviewStepDto,
  StepStatus
} from '../../models/interview-step.models';

@Injectable({
  providedIn: 'root'
})
export class InterviewStepService {
  private readonly API_URL = `${environment.apiUrl}/interview-steps`;

  constructor(private readonly http: HttpClient) {}

  getByProjectIdOrderByDate(projectId: number): Observable<InterviewStepDto[]> {
    return this.http.get<InterviewStepDto[]>(`${this.API_URL}/by-project/${projectId}/ordered`);
  }

  getByFreelanceIdAndStatus(freelanceId: number, status?: StepStatus): Observable<InterviewStepDto[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<InterviewStepDto[]>(`${this.API_URL}/by-freelance/${freelanceId}`, { params });
  }

  create(interviewStep: CreateInterviewStepDto): Observable<InterviewStepDto> {
    return this.http.post<InterviewStepDto>(this.API_URL, interviewStep);
  }

  update(id: number, interviewStep: UpdateInterviewStepDto): Observable<InterviewStepDto> {
    return this.http.put<InterviewStepDto>(`${this.API_URL}/${id}`, interviewStep);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  // Status management operations
  updateStatus(id: number, status: StepStatus): Observable<InterviewStepDto> {
    const params = new HttpParams().set('status', status);
    return this.http.patch<InterviewStepDto>(`${this.API_URL}/${id}/status`, null, { params });
  }

}
