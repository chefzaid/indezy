import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { CommuteInfoDto } from '../../../../models/commute.models';
import { CommuteService } from '../../../../services/commute/commute.service';
import { ProjectCommuteComponent } from './project-commute.component';

describe('ProjectCommuteComponent', () => {
  let commute: jasmine.SpyObj<CommuteService>;
  let fixture: ComponentFixture<ProjectCommuteComponent>;

  const trip = (overrides: Partial<CommuteInfoDto>): CommuteInfoDto => ({
    projectId: 42, projectRole: 'Java lead', origin: 'Issou', destination: 'Montrouge', travelMode: 'DRIVING',
    durationInSeconds: 3000, rushDurationInSeconds: 6300, distanceInMeters: 56000, remote: false, onsiteDaysPerMonth: 20,
    monthlySeconds: 6300 * 2 * 20, estimated: true, ...overrides
  });

  function render(trips: CommuteInfoDto[] | Error): HTMLElement {
    commute = jasmine.createSpyObj('CommuteService', ['getProjectCommute']);
    commute.getProjectCommute.and.returnValue(trips instanceof Error ? throwError(() => trips) : of(trips));
    TestBed.configureTestingModule({
      imports: [ProjectCommuteComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: CommuteService, useValue: commute }]
    });
    fixture = TestBed.createComponent(ProjectCommuteComponent);
    fixture.componentRef.setInput('projectId', 42);
    fixture.componentInstance.ngOnChanges();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows each way there off-peak and at rush hour, its month and what a remote day gives back', () => {
    const element = render([trip({}), trip({ travelMode: 'TRANSIT', durationInSeconds: 5400, rushDurationInSeconds: 6000,
      monthlySeconds: 6000 * 2 * 20 })]);
    expect(commute.getProjectCommute).toHaveBeenCalledWith(42);
    const car = element.querySelector('[data-commute-mode="DRIVING"]')!.textContent!.replace(/\s+/g, ' ');
    expect(car).toContain('≈ 50 min (56 km)');
    expect(fixture.componentInstance.rush(fixture.componentInstance.trips[0])).toBe('≈ 1 h 45');
    expect(element.querySelector('[data-commute-mode="TRANSIT"]')).not.toBeNull();
    // The quickest way at rush hour is transit, 100 minutes: twice a day, four days a month.
    expect(fixture.componentInstance.remoteDaySaving()).toBe(13.3);
    expect(fixture.componentInstance.monthlyHours(trip({}))).toBe(70);
    expect(element.querySelector('[data-remote-day-saving]')).not.toBeNull();
  });

  it('says why a trip has no time, linking the profile for the home address', () => {
    const element = render([trip({ durationInSeconds: undefined, rushDurationInSeconds: null, unavailable: 'NO_HOME_ADDRESS',
      estimated: false, durationText: undefined })]);
    expect(element.querySelector('.unavailable a')?.getAttribute('href')).toBe('/profile');
    expect(fixture.componentInstance.remoteDaySaving()).toBeNull();
  });

  it('uses Google\'s words and hides the rush hour time when it matches', () => {
    render([trip({ durationText: '48 mins', distanceText: '55 km', rushDurationInSeconds: 3000, estimated: false })]);
    expect(fixture.componentInstance.time(fixture.componentInstance.trips[0])).toBe('48 mins');
    expect(fixture.componentInstance.rush(fixture.componentInstance.trips[0])).toBeNull();
    expect(fixture.componentInstance.distance(fixture.componentInstance.trips[0])).toBe('55 km');
  });

  it('shows nothing for a remote mission, nor when the lookup fails', () => {
    expect(render([trip({ remote: true })]).querySelector('[data-project-commute]')).toBeNull();
    TestBed.resetTestingModule();
    expect(render(new Error('down')).querySelector('[data-project-commute]')).toBeNull();
  });

  it('gives back nothing without a day on site each week', () => {
    render([trip({ onsiteDaysPerMonth: 2 })]);
    expect(fixture.componentInstance.remoteDaySaving()).toBeNull();
  });
});
