import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PublicProfile, PublicProfileSettings } from '../../models';

/** The account's public profile: its settings, and the page anyone with the link reads. */
@Injectable({
  providedIn: 'root'
})
export class PublicProfileService {
  private readonly API_URL = environment.apiUrl;

  constructor(private readonly http: HttpClient) {}

  settings(): Observable<PublicProfileSettings> {
    return this.http.get<PublicProfileSettings>(`${this.API_URL}/users/public-profile`);
  }

  save(settings: PublicProfileSettings): Observable<PublicProfileSettings> {
    return this.http.put<PublicProfileSettings>(`${this.API_URL}/users/public-profile`, settings);
  }

  /** A shown profile; 404 when the address is unknown or the profile turned off. */
  view(slug: string): Observable<PublicProfile> {
    return this.http.get<PublicProfile>(`${this.API_URL}/public/profiles/${encodeURIComponent(slug)}`);
  }
}
