import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { 
  InterviewStepDto, 
  CreateInterviewStepDto, 
  UpdateInterviewStepDto, 
  KanbanBoardDto, 
  StepTransitionDto, 
  StepStatus 
} from '../../models/interview-step.models';

@Injectable({
  providedIn: 'root'
})
export class InterviewStepService {
  private readonly API_URL = `${environment.apiUrl}/interview-steps`;

  constructor(private readonly http: HttpClient) {}

  // Basic CRUD operations
  getAll(): Observable<InterviewStepDto[]> {
    return this.http.get<InterviewStepDto[]>(this.API_URL);
  }

  getById(id: number): Observable<InterviewStepDto> {
    return this.http.get<InterviewStepDto>(`${this.API_URL}/${id}`);
  }

  getByProjectId(projectId: number): Observable<InterviewStepDto[]> {
    return this.http.get<InterviewStepDto[]>(`${this.API_URL}/by-project/${projectId}`);
  }

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

  scheduleStep(id: number, date: Date): Observable<InterviewStepDto> {
    const params = new HttpParams().set('date', date.toISOString());
    return this.http.patch<InterviewStepDto>(`${this.API_URL}/${id}/schedule`, null, { params });
  }

  markAsWaitingFeedback(id: number): Observable<InterviewStepDto> {
    return this.http.patch<InterviewStepDto>(`${this.API_URL}/${id}/waiting-feedback`, null);
  }

  markAsValidated(id: number): Observable<InterviewStepDto> {
    return this.http.patch<InterviewStepDto>(`${this.API_URL}/${id}/validate`, null);
  }

  markAsFailed(id: number): Observable<InterviewStepDto> {
    return this.http.patch<InterviewStepDto>(`${this.API_URL}/${id}/fail`, null);
  }

  markAsCanceled(id: number): Observable<InterviewStepDto> {
    return this.http.patch<InterviewStepDto>(`${this.API_URL}/${id}/cancel`, null);
  }

  // Kanban board operations
  getKanbanBoard(freelanceId: number): Observable<KanbanBoardDto> {
    return this.http.get<KanbanBoardDto>(`${this.API_URL}/kanban/${freelanceId}`);
  }

  transitionProjectToNextStep(transition: StepTransitionDto): Observable<InterviewStepDto> {
    return this.http.post<InterviewStepDto>(`${this.API_URL}/transition`, transition);
  }

  // Utility methods for UI
  getStatusLabel(status: StepStatus): string {
    const labels: { [key in StepStatus]: string } = {
      [StepStatus.TO_PLAN]: 'À planifier',
      [StepStatus.PLANNED]: 'Planifié',
      [StepStatus.CANCELED]: 'Annulé',
      [StepStatus.WAITING_FEEDBACK]: 'En attente de retour',
      [StepStatus.VALIDATED]: 'Validé',
      [StepStatus.FAILED]: 'Échoué'
    };
    return labels[status] || status;
  }

  getStatusColor(status: StepStatus): string {
    const colors: { [key in StepStatus]: string } = {
      [StepStatus.TO_PLAN]: '#ffa726',
      [StepStatus.PLANNED]: '#42a5f5',
      [StepStatus.CANCELED]: '#bdbdbd',
      [StepStatus.WAITING_FEEDBACK]: '#ffee58',
      [StepStatus.VALIDATED]: '#66bb6a',
      [StepStatus.FAILED]: '#ef5350'
    };
    return colors[status] || '#bdbdbd';
  }

  isStepCompleted(status: StepStatus): boolean {
    return status === StepStatus.VALIDATED;
  }

  isStepFailed(status: StepStatus): boolean {
    return status === StepStatus.FAILED;
  }

  isStepActive(status: StepStatus): boolean {
    return status === StepStatus.PLANNED || status === StepStatus.WAITING_FEEDBACK;
  }

  canTransitionToNext(status: StepStatus): boolean {
    return status === StepStatus.VALIDATED || status === StepStatus.WAITING_FEEDBACK;
  }
}
