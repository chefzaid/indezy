import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Education, EducationDetails, Experience, ExperienceDetails } from '../../models';

/** The profile's past positions and schools, printed in the dossier de compétences. */
@Injectable({ providedIn: 'root' })
export class CareerService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/users`;

  experiences(): Observable<Experience[]> {
    return this.http.get<Experience[]>(`${this.url}/experiences`);
  }

  createExperience(details: ExperienceDetails): Observable<Experience> {
    return this.http.post<Experience>(`${this.url}/experiences`, details);
  }

  updateExperience(id: number, details: ExperienceDetails): Observable<Experience> {
    return this.http.put<Experience>(`${this.url}/experiences/${id}`, details);
  }

  deleteExperience(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/experiences/${id}`);
  }

  educations(): Observable<Education[]> {
    return this.http.get<Education[]>(`${this.url}/educations`);
  }

  createEducation(details: EducationDetails): Observable<Education> {
    return this.http.post<Education>(`${this.url}/educations`, details);
  }

  updateEducation(id: number, details: EducationDetails): Observable<Education> {
    return this.http.put<Education>(`${this.url}/educations/${id}`, details);
  }

  deleteEducation(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/educations/${id}`);
  }
}
