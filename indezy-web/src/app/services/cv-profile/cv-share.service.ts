import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreatedCvShare, CvShare, PublicCvShare } from '../../models';

export const CV_TOKEN_HEADER = 'X-Cv-Token';

/** The links CV profiles are shared through, and what such a link shows without an account. */
@Injectable({ providedIn: 'root' })
export class CvShareService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl;

  list(): Observable<CvShare[]> {
    return this.http.get<CvShare[]>(`${this.url}/users/cv-shares`);
  }

  create(profileId: number, recipient: string, layout: 'NAMED' | 'ANONYMOUS', days: number): Observable<CreatedCvShare> {
    return this.http.post<CreatedCvShare>(`${this.url}/users/cv-shares`, { profileId, recipient, layout, days });
  }

  revoke(id: number): Observable<CvShare> {
    return this.http.put<CvShare>(`${this.url}/users/cv-shares/${id}/revoke`, null);
  }

  /** What the link shows; the token travels in a header, never in the address. */
  open(token: string): Observable<PublicCvShare> {
    return this.http.get<PublicCvShare>(`${this.url}/public/cv-shares`, { headers: new HttpHeaders({ [CV_TOKEN_HEADER]: token }) });
  }

  dossier(token: string, format: 'PDF' | 'DOCX'): Observable<Blob> {
    return this.http.get(`${this.url}/public/cv-shares/dossier`, { headers: new HttpHeaders({ [CV_TOKEN_HEADER]: token }),
      params: format === 'DOCX' ? { format } : {}, responseType: 'blob' });
  }
}
