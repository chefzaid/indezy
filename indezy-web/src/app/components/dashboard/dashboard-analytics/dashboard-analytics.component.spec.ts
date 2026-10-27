import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../../assets/i18n/en.json';
import { DashboardStatsDto, FunnelBreakdown } from '../../../models';
import { DashboardAnalyticsComponent } from './dashboard-analytics.component';

function stats(overrides: Partial<DashboardStatsDto> = {}): DashboardStatsDto {
  return {
    totalProjects: 12, averageDailyRate: 600, totalEstimatedRevenue: 120000, forecastRevenue: 80000, activeProjects: 4,
    wonProjects: 3, lostProjects: 1, totalBenchDays: 0, benchPeriods: 0, estimatedBenchCost: 0,
    projectsByStatus: { IDEA: 2, WON: 3, UNKNOWN: 1 }, projectsByWorkMode: { REMOTE: 5, HYBRID: 2 },
    lostReasonsBreakdown: { RATE: 1, TIMING: 3, OTHER: 0 }, dailyRateRanges: [], sourceRoi: [], dailyRateEvolution: [],
    conversionFunnel: [], funnelBySource: [], funnelByClientType: [], funnelByEsn: [], missionsEndingSoon: [],
    staleOpportunities: [], upcomingRenewals: [], onThisDay: [], dormantContacts: [], skillTrends: [], processDurations: [],
    activityHeatmap: [], ...overrides
  };
}

const funnel = (group: string, counts: number[], rate: number): FunnelBreakdown => ({
  group, stages: counts.map((count, index) => ({ stage: `S${index}`, count, conversionRate: index === counts.length - 1 ? rate : 100 }))
});

describe('DashboardAnalyticsComponent', () => {
  let fixture: ComponentFixture<DashboardAnalyticsComponent>;
  let component: DashboardAnalyticsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardAnalyticsComponent, TranslateModule.forRoot()]
    });
    fixture = TestBed.createComponent(DashboardAnalyticsComponent);
    component = fixture.componentInstance;
  });

  function render(value: DashboardStatsDto | null): HTMLElement {
    fixture.componentRef.setInput('stats', value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function rows(): string[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.data-row'))
      .map(row => Array.from(row.children).map(cell => cell.textContent!.trim()).join(' '));
  }

  it('shows the outcomes of the statistics under the analytics title', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');

    const element = render(stats());

    expect(element.querySelector('.section-title')!.textContent).toContain('Analytics');
    expect(Array.from(element.querySelectorAll('.summary-value')).map(value => value.textContent)).toEqual(['3', '1', '4']);
  });

  it('lists the lost reasons that occurred, most frequent first', () => {
    render(stats());

    expect(component.lostReasons).toEqual([{ reason: 'TIMING', count: 3 }, { reason: 'RATE', count: 1 }]);
    expect(rows()).toEqual(['projects.lostReason.TIMING 3', 'projects.lostReason.RATE 1']);
  });

  it('copes with statistics without lost reasons', () => {
    render(stats({ lostReasonsBreakdown: undefined as never }));

    expect(component.lostReasons).toEqual([]);
    expect(text()).not.toContain('dashboard.charts.lostReasons');
  });

  it('shows every insight the statistics contain', () => {
    const element = render(stats({
      targetDailyRate: 650, averageObtainedDailyRate: 600,
      conversionFunnel: [{ stage: 'IDEA', count: 6, conversionRate: 100 }, { stage: 'WON', count: 2, conversionRate: 33.3 }],
      sourceRoi: [{ sourceName: 'Malt', totalProjects: 5, wonProjects: 2, conversionRate: 40 }],
      skillTrends: Array.from({ length: 10 }, (_, index) => ({ skill: `Skill${index}`, count: 10 - index, averageDailyRate: 600 })),
      dailyRateEvolution: [
        { period: '2025', averageAskedRate: 620, averageObtainedRate: 0, projectCount: 3 },
        { period: '2026', averageAskedRate: 0, averageObtainedRate: 640, projectCount: 2 }
      ],
      processDurations: [{ group: 'Malt', averageDays: 21.4, count: 2 }],
      benchPeriods: 2, totalBenchDays: 45, estimatedBenchCost: 27000,
      funnelByClientType: [funnel('DIRECT', [4, 2, 1], 25)],
      funnelBySource: [funnel('LinkedIn', [6, 3], 50)],
      funnelByEsn: [funnel('Capgemini', [3, 1], 33.3)],
      activityHeatmap: [{ date: '2026-10-01', count: 2 }]
    }));

    expect(element.querySelector('app-rate-target')).not.toBeNull();
    expect(rows()).toEqual(jasmine.arrayContaining([
      'projects.statuses.WON 2 33.3 %',
      'Malt 2/5 40 %',
      'Skill7 3 600 €',
      '2025 620 € —',
      '2026 — 640 €',
      'Malt 21 2',
      'dashboard.charts.benchDays 45',
      'dashboard.charts.benchCost 27,000 €',
      'dashboard.charts.clientType.DIRECT 4 → 2 → 1 25 %',
      'LinkedIn 6 → 3 50 %',
      'Capgemini 3 → 1 33.3 %'
    ]));
    expect(text()).not.toContain('Skill8');
    expect(component.getTopSkillTrends().length).toBe(DashboardAnalyticsComponent.TOP_SKILLS);
    expect(element.querySelector('app-activity-heatmap')).not.toBeNull();
  });

  it('hides the insights the statistics lack', () => {
    const element = render(stats());

    expect(element.querySelector('app-rate-target')).toBeNull();
    expect(element.querySelector('app-activity-heatmap')).toBeNull();
    expect(text()).not.toContain('dashboard.charts.conversionFunnel');
    expect(text()).not.toContain('dashboard.charts.benchTime');
  });

  it('renders nothing and returns empty insights without statistics', () => {
    const element = render(null);

    expect(element.querySelector('.insights-grid')).toBeNull();
    expect(component.lostReasons).toEqual([]);
    expect(component.getSourceRoi()).toEqual([]);
    expect(component.getDailyRateEvolution()).toEqual([]);
    expect(component.getConversionFunnel()).toEqual([]);
    expect(component.getFunnelBySource()).toEqual([]);
    expect(component.getFunnelByClientType()).toEqual([]);
    expect(component.getFunnelByEsn()).toEqual([]);
    expect(component.getSkillTrends()).toEqual([]);
    expect(component.getProcessDurations()).toEqual([]);
    expect(component.getActivityHeatmap()).toEqual([]);
    expect(component.funnelConversion({ group: 'none', stages: [] })).toBe(0);
  });
});
