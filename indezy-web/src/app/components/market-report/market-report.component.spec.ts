import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth/auth.service';
import { MarketReport, MarketReportComponent } from './market-report.component';

describe('MarketReportComponent', () => {
  const report: MarketReport = {
    quarter: '2026-Q3', from: '2026-07-01', to: '2026-09-30', offers: 120, threshold: 10, overall: { offers: 120, median: 600, low: 520, high: 680 },
    skills: [{ skill: 'Java', figure: { offers: 40, median: 620, low: 560, high: 700 }, previousMedian: 600, bySeniority: { SENIOR: 650 },
      byRegion: {} }],
    regions: [{ regionCode: '11', name: 'Île-de-France', figure: { offers: 60, median: 640, low: 580, high: 700 } }],
    quarters: ['2026-Q3', '2026-Q2']
  };

  function create() {
    TestBed.configureTestingModule({
      imports: [MarketReportComponent, TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: AuthService, useValue: { isAuthenticated: () => false } }]
    });
    const fixture = TestBed.createComponent(MarketReportComponent);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController) };
  }

  it('shows the last quarter by skill, seniority and region, and another quarter on request', () => {
    const { fixture, http } = create();
    http.expectOne(`${environment.apiUrl}/public/market-report`).flush(report);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-market-report-skill="Java"]')?.textContent).toContain('+3 %');
    expect(element.querySelector('[data-market-report-regions]')?.textContent).toContain('Île-de-France');
    expect(element.querySelector('app-public-page-header')).not.toBeNull();
    fixture.componentInstance.load('2026-Q2');
    http.expectOne(request => request.params.get('quarter') === '2026-Q2').flush({ ...report, quarter: '2026-Q2', overall: null, skills: [],
      regions: [] });
    fixture.detectChanges();
    expect(element.querySelector('[data-market-report-empty]')).not.toBeNull();
    expect(fixture.componentInstance.change(500, null)).toBe('—');
  });

  it('says when the report cannot be loaded', () => {
    const { fixture, http } = create();
    http.expectOne(`${environment.apiUrl}/public/market-report`).flush('down', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('marketReport.loadError');
  });
});
