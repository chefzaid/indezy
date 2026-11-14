import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Training, TrainingDetails } from '../../models';

/** The training record: trainings, their certification and the funding asked for them. */
@Injectable({ providedIn: 'root' })
export class TrainingService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/trainings`;

  list(): Observable<Training[]> {
    return this.http.get<Training[]>(this.url);
  }

  create(details: TrainingDetails): Observable<Training> {
    return this.http.post<Training>(this.url, details);
  }

  update(id: number, details: TrainingDetails): Observable<Training> {
    return this.http.put<Training>(`${this.url}/${id}`, details);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
