import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ProjectCommuteDto } from '../../../models/commute.models';
import { ProjectDto } from '../../../models/project.models';
import { AuthService } from '../../../services/auth/auth.service';
import { CommuteService } from '../../../services/commute/commute.service';
import { commuteStats } from './commute-stats';
import { CommuteStatsComponent } from './commute-stats.component';

function result(status: string, dailyRate: number, hours: number | null, onsite: number): ProjectCommuteDto {
  return {
    project: { id: dailyRate, status, dailyRate } as ProjectDto,
    commute: { projectId: dailyRate, projectRole: 'Role', origin: 'Paris', destination: 'Lyon', travelMode: 'DRIVING',
      remote: onsite === 0, onsiteDaysPerMonth: onsite, monthlySeconds: hours === null ? null : hours * 3600 }
  };
}

describe('commuteStats', () => {
  it('averages the pipeline and the won missions, the remote share and the rates of long and short commutes', () => {
    expect(commuteStats([
      result('CONTACT', 600, 10, 20), result('OFFER', 700, 30, 10), result('WON', 650, 0, 0), result('WON', 550, 12, 10),
      result('LOST', 900, 50, 20), result('INTERVIEW', 500, null, 20)
    ])).toEqual({ pipelineHours: 20, wonHours: 6, remotePercent: 40, longRate: 700, shortRate: 600 });
  });

  it('leaves out what it cannot say', () => {
    expect(commuteStats([result('WON', 650, 12, 10)])).toEqual({ pipelineHours: null, wonHours: 12, remotePercent: 50,
      longRate: null, shortRate: null });
    expect(commuteStats([])).toEqual({ pipelineHours: null, wonHours: null, remotePercent: null, longRate: null, shortRate: null });
  });
});

describe('CommuteStatsComponent', () => {
  function render(answer: ProjectCommuteDto[] | Error, user: { id: number } | null = { id: 1 }): HTMLElement {
    const commutes = jasmine.createSpyObj('CommuteService', ['getProjectsSortedByCommute']);
    commutes.getProjectsSortedByCommute.and.returnValue(answer instanceof Error ? throwError(() => answer) : of(answer));
    TestBed.configureTestingModule({
      imports: [CommuteStatsComponent, TranslateModule.forRoot()],
      providers: [{ provide: CommuteService, useValue: commutes }, { provide: AuthService, useValue: { getUser: () => user } }]
    });
    const fixture = TestBed.createComponent(CommuteStatsComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the figures once a commute is known', () => {
    const element = render([result('CONTACT', 600, 10, 20), result('OFFER', 700, 30, 10)]);
    expect(element.querySelector('[data-commute-remote]')?.textContent).toContain('25 %');
    expect(element.querySelector('[data-commute-rates]')).not.toBeNull();
    expect(element.style.display).toBe('');
  });

  it('hides without a known commute, a user, or an answer', () => {
    expect(render([result('LOST', 600, 10, 20)]).style.display).toBe('none');
    TestBed.resetTestingModule();
    expect(render(new Error('down')).style.display).toBe('none');
    TestBed.resetTestingModule();
    expect(render([], null).style.display).toBe('none');
  });
});
