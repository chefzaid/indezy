import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProjectAttachment } from '../../models/project.models';

/** The files kept with an opportunity. */
@Injectable({
  providedIn: 'root'
})
export class ProjectAttachmentService {
  /** The largest file the server keeps. */
  static readonly MAX_BYTES = 10 * 1024 * 1024;
  private readonly API_URL = `${environment.apiUrl}/projects`;

  constructor(private readonly http: HttpClient) {}

  list(projectId: number): Observable<ProjectAttachment[]> {
    return this.http.get<ProjectAttachment[]>(`${this.API_URL}/${projectId}/attachments`);
  }

  upload(projectId: number, file: File): Observable<ProjectAttachment> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<ProjectAttachment>(`${this.API_URL}/${projectId}/attachments`, form);
  }

  /** A file's content, read with the session's credentials so it can be shown or saved. */
  content(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/attachments/${id}/content`, { responseType: 'blob' });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/attachments/${id}`);
  }
}
