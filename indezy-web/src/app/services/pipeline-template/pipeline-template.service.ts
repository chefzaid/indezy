import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InterviewStepDto, PipelineTemplate } from '../../models';

/** Recruitment processes saved from an opportunity's steps and applied to another one. */
@Injectable({
  providedIn: 'root'
})
export class PipelineTemplateService {
  private readonly API_URL = `${environment.apiUrl}/pipeline-templates`;

  constructor(private readonly http: HttpClient) {}

  /** The workspace's templates, by name. */
  list(): Observable<PipelineTemplate[]> {
    return this.http.get<PipelineTemplate[]>(this.API_URL);
  }

  /** Saves the opportunity's steps under {@code name}, for a client or ESN or, without one, for anyone. */
  create(name: string, projectId: number, clientId?: number): Observable<PipelineTemplate> {
    return this.http.post<PipelineTemplate>(this.API_URL, { name, projectId, clientId });
  }

  /** Adds the template's steps to the opportunity, to plan; answers the steps added. */
  apply(id: number, projectId: number): Observable<InterviewStepDto[]> {
    return this.http.post<InterviewStepDto[]>(`${this.API_URL}/${id}/apply/${projectId}`, null);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
