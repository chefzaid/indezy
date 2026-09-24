import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ALL_SEASONS, SeasonService } from './season.service';
import { Season } from '../../models/season.models';
import { environment } from '../../../environments/environment';

describe('SeasonService', () => {
  let service: SeasonService;
  let httpMock: HttpTestingController;

  const autumn: Season = { id: 2, name: 'Autumn search', startDate: '2026-09-01', active: true };
  const spring: Season = { id: 1, name: 'Spring search', startDate: '2026-03-01', endDate: '2026-06-30', active: false };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(SeasonService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists the seasons of a workspace', () => {
    service.getByFreelanceId(7).subscribe(seasons => expect(seasons).toEqual([autumn, spring]));
    const req = httpMock.expectOne(`${environment.apiUrl}/seasons/by-freelance/7`);
    expect(req.request.method).toBe('GET');
    req.flush([autumn, spring]);
  });

  it('creates, updates and deletes seasons', () => {
    service.create(autumn).subscribe();
    expect(httpMock.expectOne(`${environment.apiUrl}/seasons`).request.method).toBe('POST');
    service.update(2, autumn).subscribe();
    expect(httpMock.expectOne(`${environment.apiUrl}/seasons/2`).request.method).toBe('PUT');
    service.delete(2).subscribe();
    expect(httpMock.expectOne(`${environment.apiUrl}/seasons/2`).request.method).toBe('DELETE');
  });

  describe('pickInitialSelection', () => {
    it('restores the remembered season when it still exists', () => {
      expect(SeasonService.pickInitialSelection([autumn, spring], '1')).toBe(1);
    });

    it('keeps "all seasons" when that was the last choice', () => {
      expect(SeasonService.pickInitialSelection([autumn, spring], ALL_SEASONS)).toBe(ALL_SEASONS);
    });

    it('falls back to the running season, then to every season', () => {
      expect(SeasonService.pickInitialSelection([autumn, spring], '99')).toBe(2);
      expect(SeasonService.pickInitialSelection([spring], null)).toBe(ALL_SEASONS);
      expect(SeasonService.pickInitialSelection([], null)).toBe(ALL_SEASONS);
    });
  });
});
