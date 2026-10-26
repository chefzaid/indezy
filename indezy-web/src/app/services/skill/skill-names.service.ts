import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SkillNames } from '../../shared/utils/skill-names';

/** The known skills and their aliases, read once from the server; without them, skills are compared by spelling only. */
@Injectable({ providedIn: 'root' })
export class SkillNamesService {
  private readonly http = inject(HttpClient);

  readonly names$: Observable<SkillNames> = this.http.get<Record<string, string[]>>(`${environment.apiUrl}/skills/aliases`).pipe(
    map(aliases => new SkillNames(aliases)),
    catchError(() => of(SkillNames.NONE)),
    shareReplay(1)
  );
}
