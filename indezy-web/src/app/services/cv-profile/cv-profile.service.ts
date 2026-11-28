import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CvAudit, CvProfile, CvProfileDetails, CvProfileSources, DocumentLanguage } from '../../models';

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

  /** The CV profile closest to a feed offer's skills; null without a profile. */
  closestToOffer(offerId: number): Observable<CvProfile | null> {
    return this.http.get<CvProfile>(`${this.url}/closest`, { params: { offerId }, observe: 'response' })
      .pipe(map(response => response.status === 204 ? null : response.body));
  }

  sources(): Observable<CvProfileSources> {
    return this.http.get<CvProfileSources>(`${this.url}/sources`);
  }

  create(details: CvProfileDetails): Observable<CvProfile> {
    return this.http.post<CvProfile>(this.url, details);
  }

  /** What a profile written so would look like, unsaved: its dossier's first page as a PNG image. */
  preview(details: CvProfileDetails, layout: 'NAMED' | 'ANONYMOUS'): Observable<Blob> {
    return this.http.post(`${this.url}/preview`, details, { params: { layout }, responseType: 'blob' });
  }

  update(id: number, details: CvProfileDetails): Observable<CvProfile> {
    return this.http.put<CvProfile>(`${this.url}/${id}`, details);
  }

  /** The profile's wording reviewed against the facts as they are now: its changes are cleared. */
  reviewed(id: number): Observable<CvProfile> {
    return this.http.put<CvProfile>(`${this.url}/${id}/reviewed`, null);
  }

  /** A copy of the profile, to change on its own. */
  duplicate(id: number): Observable<CvProfile> {
    return this.http.post<CvProfile>(`${this.url}/${id}/duplicate`, null);
  }

  /** Archives the profile, or brings it back. */
  archive(id: number, archived: boolean): Observable<CvProfile> {
    return this.http.put<CvProfile>(`${this.url}/${id}/archived`, { archived });
  }

  makeDefault(id: number): Observable<CvProfile> {
    return this.http.put<CvProfile>(`${this.url}/${id}/default`, null);
  }

  /** The profile's dossier checked before it goes out, named or anonymized; no AI. */
  audit(id: number, layout: 'NAMED' | 'ANONYMOUS'): Observable<CvAudit> {
    return this.http.get<CvAudit>(`${this.url}/${id}/audit`, { params: { layout } });
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
