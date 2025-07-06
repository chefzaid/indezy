import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SourceService } from './source.service';
import { SourceDto, SourceType } from '../../models';
import { environment } from '../../../environments/environment';

describe('SourceService', () => {
  let service: SourceService;
  let httpMock: HttpTestingController;

  const mockSource: SourceDto = {
    id: 1,
    name: 'LinkedIn',
    type: 'JOB_BOARD',
    link: 'https://linkedin.com',
    isListing: false,
    popularityRating: 4,
    usefulnessRating: 5,
    notes: 'Professional networking platform',
    freelanceId: 1,
    totalProjects: 3,
    averageDailyRate: 600,
    createdAt: new Date('2024-01-15'),
    updatedAt: new Date('2024-01-15')
  };

  const mockSources: SourceDto[] = [
    mockSource,
    {
      ...mockSource,
      id: 2,
      name: 'Indeed',
      type: 'JOB_BOARD',
      link: 'https://indeed.com',
      popularityRating: 3,
      usefulnessRating: 4
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SourceService]
    });

    service = TestBed.inject(SourceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should get all sources', () => {
    service.getAll().subscribe(sources => {
      expect(sources).toEqual(mockSources);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/sources`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSources);
  });

  it('should get source by id', () => {
    const sourceId = 1;

    service.getById(sourceId).subscribe(source => {
      expect(source).toEqual(mockSource);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/sources/${sourceId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSource);
  });

  it('should get sources by freelance id', () => {
    const freelanceId = 1;

    service.getByFreelanceId(freelanceId).subscribe(sources => {
      expect(sources).toEqual(mockSources);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/sources/by-freelance/${freelanceId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSources);
  });

  it('should create source', () => {
    const newSource: SourceDto = {
      name: 'New Source',
      type: 'SOCIAL_MEDIA',
      freelanceId: 1
    };

    service.create(newSource).subscribe(source => {
      expect(source).toEqual(mockSource);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/sources`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(newSource);
    req.flush(mockSource);
  });

  it('should update source', () => {
    const sourceId = 1;
    const updatedSource: SourceDto = {
      ...mockSource,
      name: 'Updated Source'
    };

    service.update(sourceId, updatedSource).subscribe(source => {
      expect(source).toEqual(updatedSource);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/sources/${sourceId}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updatedSource);
    req.flush(updatedSource);
  });

  it('should delete source', () => {
    const sourceId = 1;

    service.delete(sourceId).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/sources/${sourceId}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should get average ratings', () => {
    const freelanceId = 1;
    const mockRatings = { averagePopularityRating: 4.0, averageUsefulnessRating: 4.5 };

    service.getAverageRatings(freelanceId).subscribe(ratings => {
      expect(ratings).toEqual(mockRatings);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/sources/by-freelance/${freelanceId}/average-ratings`);
    expect(req.request.method).toBe('GET');
    req.flush(mockRatings);
  });

  it('should search sources with filters', () => {
    const freelanceId = 1;
    const filters = {
      type: 'JOB_BOARD' as SourceType,
      minPopularityRating: 3,
      maxPopularityRating: 5,
      isListing: false
    };

    service.searchSources(freelanceId, filters).subscribe(sources => {
      expect(sources).toEqual(mockSources);
    });

    const req = httpMock.expectOne(req => 
      req.url === `${environment.apiUrl}/sources/by-freelance/${freelanceId}/search` &&
      req.params.get('type') === 'JOB_BOARD' &&
      req.params.get('minPopularityRating') === '3' &&
      req.params.get('maxPopularityRating') === '5' &&
      req.params.get('isListing') === 'false'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockSources);
  });

  it('should get source types', () => {
    const types = service.getSourceTypes();
    expect(types).toEqual(['JOB_BOARD', 'SOCIAL_MEDIA', 'EMAIL', 'CALL', 'SMS']);
  });

  it('should get source type labels', () => {
    expect(service.getSourceTypeLabel('JOB_BOARD')).toBe('Job Board');
    expect(service.getSourceTypeLabel('SOCIAL_MEDIA')).toBe('Social Media');
    expect(service.getSourceTypeLabel('EMAIL')).toBe('Email');
    expect(service.getSourceTypeLabel('CALL')).toBe('Call');
    expect(service.getSourceTypeLabel('SMS')).toBe('SMS');
  });
});
