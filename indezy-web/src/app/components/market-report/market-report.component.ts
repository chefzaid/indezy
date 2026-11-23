import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth/auth.service';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';

/** A figure: how many missions back it, their median daily rate and middle half. */
export interface MarketFigure {
  offers: number;
  median: number;
  low: number;
  high: number;
}

export interface MarketReport {
  quarter: string;
  from: string;
  to: string;
  offers: number;
  threshold: number;
  overall: MarketFigure | null;
  skills: { skill: string; figure: MarketFigure; previousMedian: number | null; bySeniority: Record<string, number>;
    byRegion: Record<string, number> }[];
  regions: { regionCode: string; name: string; figure: MarketFigure }[];
  quarters: string[];
}

const SENIORITIES = ['JUNIOR', 'CONFIRMED', 'SENIOR', 'EXPERT'];

/**
 * The quarterly market report (`/market-report`, open without an account, see docs/features.md#market-report): the daily
 * rates the feed's offers published in a quarter state, by skill, seniority and region, each figure backed by enough
 * missions or not given.
 */
@Component({
  selector: 'app-market-report',
  imports: [DatePipe, DecimalPipe, FormsModule, MatFormFieldModule, MatProgressSpinnerModule, MatSelectModule, TranslateModule,
    PublicPageHeaderComponent],
  template: `
    @if (!auth.isAuthenticated()) {
      <app-public-page-header></app-public-page-header>
    }
    <div class="report" data-market-report>
      <header>
        <h1>{{ 'marketReport.title' | translate }}</h1>
        <p class="intro">{{ 'marketReport.intro' | translate:{ threshold: report?.threshold ?? 10 } }}</p>
        @if (report) {
          <mat-form-field appearance="outline" subscriptSizing="dynamic" class="quarter">
            <mat-label>{{ 'marketReport.quarter' | translate }}</mat-label>
            <mat-select [ngModel]="report.quarter" (ngModelChange)="load($event)" data-market-report-quarter>
              @for (quarter of report.quarters; track quarter) {
                <mat-option [value]="quarter">{{ quarter }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
      </header>
      @if (loading) {
        <mat-spinner diameter="32" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
      } @else if (report) {
        <p class="period">{{ 'marketReport.period' | translate:{ from: (report.from | date:'longDate'), to: (report.to | date:'longDate'),
          count: (report.offers | number) } }}</p>
        @if (report.overall; as overall) {
          <section class="card overall" data-market-report-overall>
            <h2>{{ 'marketReport.overall' | translate }}</h2>
            <p class="big">{{ overall.median | number }} €</p>
            <p class="range">{{ 'marketReport.range' | translate:{ low: (overall.low | number), high: (overall.high | number) } }}</p>
          </section>
        }
        @if (report.skills.length) {
          <section class="card">
            <h2>{{ 'marketReport.bySkill' | translate }}</h2>
            <div class="scroll" tabindex="0" role="region" [attr.aria-label]="'marketReport.bySkill' | translate">
              <table data-market-report-skills>
                <thead>
                  <tr>
                    <th scope="col">{{ 'marketReport.skill' | translate }}</th>
                    <th scope="col">{{ 'marketReport.missions' | translate }}</th>
                    <th scope="col">{{ 'marketReport.median' | translate }}</th>
                    <th scope="col">{{ 'marketReport.middleHalf' | translate }}</th>
                    <th scope="col">{{ 'marketReport.change' | translate }}</th>
                    @for (seniority of seniorities; track seniority) {
                      <th scope="col">{{ 'marketReport.seniorities.' + seniority | translate }}</th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (skill of report.skills; track skill.skill) {
                    <tr [attr.data-market-report-skill]="skill.skill">
                      <th scope="row">{{ skill.skill }}</th>
                      <td>{{ skill.figure.offers | number }}</td>
                      <td>{{ skill.figure.median | number }} €</td>
                      <td>{{ skill.figure.low | number }}–{{ skill.figure.high | number }} €</td>
                      <td>{{ change(skill.figure.median, skill.previousMedian) }}</td>
                      @for (seniority of seniorities; track seniority) {
                        <td>{{ skill.bySeniority[seniority] ? (skill.bySeniority[seniority] | number) + ' €' : '—' }}</td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        }
        @if (report.regions.length) {
          <section class="card">
            <h2>{{ 'marketReport.byRegion' | translate }}</h2>
            <ul class="regions" data-market-report-regions>
              @for (region of report.regions; track region.regionCode) {
                <li><span>{{ region.name }}</span><strong>{{ region.figure.median | number }} €</strong>
                  <small>{{ 'marketReport.missionsCount' | translate:{ count: region.figure.offers } }}</small></li>
              }
            </ul>
          </section>
        }
        @if (!report.overall) {
          <p class="empty" data-market-report-empty>{{ 'marketReport.empty' | translate:{ threshold: report.threshold } }}</p>
        }
        <p class="method">{{ 'marketReport.method' | translate }}</p>
      } @else {
        <p class="empty">{{ 'marketReport.loadError' | translate }}</p>
      }
    </div>
  `,
  styles: [`
    .report { display: flex; flex-direction: column; gap: 16px; width: 100%; max-width: 1080px; margin: 0 auto; padding: 0 16px 24px;
      box-sizing: border-box; }
    header { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 8px 16px; }
    header h1 { flex-basis: 100%; margin: 8px 0 0; font-size: 1.75rem; }
    .intro { flex: 1 1 320px; margin: 0; color: var(--mat-sys-on-surface-variant); }
    .quarter { width: 160px; }
    .period, .method, .empty { margin: 0; color: var(--mat-sys-on-surface-variant); font-size: .875rem; }
    .card { padding: 16px 20px; border: 1px solid var(--app-border); border-radius: 16px; background: var(--app-surface); }
    .card h2 { margin: 0 0 12px; font-size: 1.1rem; }
    .big { margin: 0; font-size: 2rem; font-weight: 700; }
    .range { margin: 4px 0 0; color: var(--mat-sys-on-surface-variant); }
    .scroll { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .875rem; }
    th, td { padding: 6px 8px; text-align: right; white-space: nowrap; border-bottom: 1px solid var(--app-border); }
    th:first-child, td:first-child { text-align: left; }
    .regions { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; margin: 0; padding: 0; list-style: none; }
    .regions li { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 8px; }
    .regions span { flex: 1; }
    .regions small { flex-basis: 100%; color: var(--mat-sys-on-surface-variant); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MarketReportComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly changes = inject(ChangeDetectorRef);
  readonly seniorities = SENIORITIES;
  report: MarketReport | null = null;
  loading = true;

  ngOnInit(): void {
    this.load(null);
  }

  load(quarter: string | null): void {
    this.loading = true;
    const params = quarter ? new HttpParams().set('quarter', quarter) : new HttpParams();
    this.http.get<MarketReport>(`${environment.apiUrl}/public/market-report`, { params }).subscribe({
      next: report => {
        this.report = report;
        this.loading = false;
        this.changes.markForCheck();
      },
      error: () => {
        this.report = null;
        this.loading = false;
        this.changes.markForCheck();
      }
    });
  }

  /** The change of the median since the previous quarter, as a signed percentage; a dash without one. */
  change(median: number, previous: number | null): string {
    if (!previous) {
      return '—';
    }
    const percent = Math.round((median - previous) * 100 / previous);
    return (percent > 0 ? '+' : '') + percent + ' %';
  }
}
