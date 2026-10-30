import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, map, of, take, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Capabilities, FeatureFlag } from '../../models';

/** Every feature on and no admin console: what an account sees until its capabilities are read, or when they cannot be. */
export const DEFAULT_CAPABILITIES: Capabilities = {
  admin: false,
  features: { OFFER_FEED: true, PUBLIC_PROFILES: true, DATA_IMPORT: true, YEARLY_REVIEW: true }
};

/** What the signed-in account may open: the admin console, and the features an administrator left on. */
@Injectable({
  providedIn: 'root'
})
export class CapabilitiesService {
  private readonly current = new BehaviorSubject<Capabilities | null>(null);

  constructor(private readonly http: HttpClient) {}

  /** Reads them again, for a new sign-in. */
  refresh(): Observable<Capabilities> {
    return this.http.get<Partial<Capabilities> | null>(`${environment.apiUrl}/users/capabilities`).pipe(
      map(read => ({ admin: read?.admin === true, features: { ...DEFAULT_CAPABILITIES.features, ...read?.features } })),
      catchError(() => of(DEFAULT_CAPABILITIES)),
      tap(capabilities => this.current.next(capabilities))
    );
  }

  /** Forgets them, at sign-out. */
  clear(): void {
    this.current.next(null);
  }

  /** The capabilities, read once when none are known yet. */
  get(): Observable<Capabilities> {
    return this.current.value ? of(this.current.value) : this.refresh().pipe(take(1));
  }

  isEnabled(flag: FeatureFlag): Observable<boolean> {
    return this.get().pipe(map(capabilities => capabilities.features[flag] !== false));
  }

  /** The last capabilities read, synchronously; every feature on before the first read. */
  snapshot(): Capabilities {
    return this.current.value ?? DEFAULT_CAPABILITIES;
  }
}
