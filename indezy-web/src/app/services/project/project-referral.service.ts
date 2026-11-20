import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ProjectReferral, ProjectReferralView } from '../../models/project.models';

/** Missions' co-optations: who referred them, and the bonuses paid or received for them. */
@Injectable({ providedIn: 'root' })
export class ProjectReferralService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/projects`;

  /** The mission's co-optation; none (204) when none was saved. */
  get(projectId: number): Observable<ProjectReferralView | null> {
    return this.http.get<ProjectReferralView | null>(`${this.url}/${projectId}/referral`);
  }

  save(projectId: number, referral: ProjectReferral): Observable<ProjectReferralView> {
    return this.http.put<ProjectReferralView>(`${this.url}/${projectId}/referral`, referral);
  }

  remove(projectId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${projectId}/referral`);
  }
}
