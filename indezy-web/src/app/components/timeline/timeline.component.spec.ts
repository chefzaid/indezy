import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TimelineComponent } from './timeline.component';
import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { AuthService } from '../../services/auth/auth.service';
import { InterviewStepDto, StepStatus } from '../../models';
import { User } from '../../models/auth.models';

describe('TimelineComponent', () => {
  let fixture: ComponentFixture<TimelineComponent>;
  let component: TimelineComponent;
  let stepServiceSpy: jasmine.SpyObj<InterviewStepService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const future = (days: number): string =>
    new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString();
  const past = (days: number): string =>
    new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();

  const steps: InterviewStepDto[] = [
    { id: 1, title: 'Far', status: StepStatus.PLANNED, projectId: 10, date: future(10) },
    { id: 2, title: 'Past', status: StepStatus.PLANNED, projectId: 11, date: past(3) },
    { id: 3, title: 'Soon', status: StepStatus.PLANNED, projectId: 12, date: future(2) },
    { id: 4, title: 'Undated', status: StepStatus.PLANNED, projectId: 13 }
  ];

  beforeEach(async () => {
    stepServiceSpy = jasmine.createSpyObj<InterviewStepService>('InterviewStepService', ['getByFreelanceIdAndStatus']);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['getUser']);

    await TestBed.configureTestingModule({
      imports: [TimelineComponent, RouterModule.forRoot([]), TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        { provide: InterviewStepService, useValue: stepServiceSpy },
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TimelineComponent);
    component = fixture.componentInstance;
  });

  it('shows only future steps, soonest first', () => {
    authServiceSpy.getUser.and.returnValue({ id: 7 } as User);
    stepServiceSpy.getByFreelanceIdAndStatus.and.returnValue(of(steps));

    fixture.detectChanges();

    expect(stepServiceSpy.getByFreelanceIdAndStatus).toHaveBeenCalledWith(7, StepStatus.PLANNED);
    expect(component.upcomingSteps.map(s => s.id)).toEqual([3, 1]);
    expect(component.isLoading).toBeFalse();
  });

  it('does not query steps when no user is authenticated', () => {
    authServiceSpy.getUser.and.returnValue(null);

    fixture.detectChanges();

    expect(stepServiceSpy.getByFreelanceIdAndStatus).not.toHaveBeenCalled();
    expect(component.upcomingSteps).toEqual([]);
  });
});
