import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { SkillNames } from '../../shared/utils/skill-names';
import { SkillNamesService } from './skill-names.service';

describe('SkillNamesService', () => {
  let http: HttpTestingController;
  let service: SkillNamesService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(SkillNamesService);
  });

  it('reads the aliases once for every reader', () => {
    const read: SkillNames[] = [];
    service.names$.subscribe(names => read.push(names));
    service.names$.subscribe(names => read.push(names));

    http.expectOne(`${environment.apiUrl}/skills/aliases`).flush({ JavaScript: ['JS'] });

    expect(read.length).toBe(2);
    expect(read[1].canonical('js')).toBe('JavaScript');
    http.verify();
  });

  it('compares skills by spelling when the aliases cannot be read', () => {
    let names: SkillNames | undefined;
    service.names$.subscribe(read => names = read);

    http.expectOne(`${environment.apiUrl}/skills/aliases`).flush('down', { status: 500, statusText: 'Server Error' });

    expect(names).toBe(SkillNames.NONE);
  });
});
