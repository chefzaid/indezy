import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CompanyReviewDraft, CompanyReviews } from '../../models';

/** What the network's members say of the workspace's clients and ESNs, and the member's own review. */
@Injectable({
  providedIn: 'root'
})
export class NetworkReviewService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/network/reviews/client`;

  company(clientId: number): Observable<CompanyReviews> {
    return this.http.get<CompanyReviews>(`${this.API_URL}/${clientId}`);
  }

  write(clientId: number, draft: CompanyReviewDraft): Observable<CompanyReviews> {
    return this.http.put<CompanyReviews>(`${this.API_URL}/${clientId}`, draft);
  }

  delete(clientId: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${clientId}`);
  }
}
