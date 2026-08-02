import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ActivityHeatmapComponent } from './activity-heatmap.component';

describe('ActivityHeatmapComponent', () => {
  let fixture: ComponentFixture<ActivityHeatmapComponent>;
  let component: ActivityHeatmapComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActivityHeatmapComponent, TranslateModule.forRoot()]
    }).compileComponents();

    fixture = TestBed.createComponent(ActivityHeatmapComponent);
    component = fixture.componentInstance;
  });

  it('maps counts to 0-4 intensity levels', () => {
    expect(component.intensity(0)).toBe(0);
    expect(component.intensity(2)).toBe(1);
    expect(component.intensity(4)).toBe(2);
    expect(component.intensity(6)).toBe(3);
    expect(component.intensity(20)).toBe(4);
  });

  it('builds week columns of seven days ending at today', () => {
    const today = new Date(2026, 6, 1); // Wed 1 Jul 2026
    const weeks = component.buildWeeks([{ date: '2026-06-30', count: 3 }], today);

    // Every column except possibly the last is a full Monday-Sunday week.
    weeks.slice(0, -1).forEach(week => expect(week.length).toBe(7));

    const cellFor = weeks.flat().find(cell => cell.date === '2026-06-30');
    expect(cellFor?.count).toBe(3);
    // A day with no recorded activity defaults to zero.
    const other = weeks.flat().find(cell => cell.date === '2026-06-29');
    expect(other?.count).toBe(0);
  });

  it('covers roughly a year of weeks', () => {
    const weeks = component.buildWeeks([], new Date(2026, 6, 1));
    expect(weeks.length).toBeGreaterThanOrEqual(52);
    expect(weeks.length).toBeLessThanOrEqual(54);
  });
});
