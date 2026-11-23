import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, Subject, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProfileCv } from '../../models';

/** The CV kept in the profile, ready to download and attach to applications. */
@Injectable({ providedIn: 'root' })
export class ProfileCvService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/users/cv`;
  /** Emits once a CV was kept or removed elsewhere, e.g. by the profile import. */
  readonly changed = new Subject<void>();

  /** The CV kept; null when none is. */
  info(): Observable<ProfileCv | null> {
    return this.http.get<ProfileCv>(`${this.url}/info`, { observe: 'response' })
      .pipe(map((response: HttpResponse<ProfileCv>) => response.status === 204 ? null : response.body));
  }

  keep(file: File): Observable<ProfileCv> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ProfileCv>(this.url, form);
  }

  download(): Observable<Blob> {
    return this.http.get(this.url, { responseType: 'blob' });
  }

  remove(): Observable<void> {
    return this.http.delete<void>(this.url);
  }
}
