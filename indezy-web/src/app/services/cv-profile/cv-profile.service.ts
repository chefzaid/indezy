import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CvProfile, CvProfileDetails, CvProfileSources, DocumentLanguage } from '../../models';

/** The workspace's CV profiles (see docs/features.md#cv-profiles). */
@Injectable({ providedIn: 'root' })
export class CvProfileService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/users/cv-profiles`;
  /** Emits once a profile was added elsewhere, e.g. saved from a CV rewrite. */
  readonly changed = new Subject<void>();

  list(): Observable<CvProfile[]> {
    return this.http.get<CvProfile[]>(this.url);
  }

  sources(): Observable<CvProfileSources> {
    return this.http.get<CvProfileSources>(`${this.url}/sources`);
  }

  create(details: CvProfileDetails): Observable<CvProfile> {
    return this.http.post<CvProfile>(this.url, details);
  }

  update(id: number, details: CvProfileDetails): Observable<CvProfile> {
    return this.http.put<CvProfile>(`${this.url}/${id}`, details);
  }

  makeDefault(id: number): Observable<CvProfile> {
    return this.http.put<CvProfile>(`${this.url}/${id}/default`, null);
  }

  /** Writes (or rewrites) the profile's twin in the other language with AI. */
  translate(id: number): Observable<CvProfile> {
    return this.http.post<CvProfile>(`${this.url}/${id}/translate`, null);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** The profile's dossier, a PDF or an editable Word document, in the profile's language. */
  dossier(id: number, layout: 'NAMED' | 'ANONYMOUS', format: 'PDF' | 'DOCX', language?: DocumentLanguage): Observable<Blob> {
    return this.http.get(`${this.url}/${id}/dossier`, { params: { layout, ...(format === 'DOCX' ? { format } : {}), ...(language ? { language } : {}) },
      responseType: 'blob' });
  }
}
