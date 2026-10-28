import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { ProjectDto, ProjectStatus } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { MissionEndActionComponent } from './mission-end-action.component';
import { MissionEndDialogComponent } from './mission-end-dialog.component';

describe('MissionEndActionComponent', () => {
  const mission = { id: 12, role: 'Développeur Java', dailyRate: 600, status: ProjectStatus.WON, startDate: '2026-01-05' } as ProjectDto;
  let projects: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialog: jasmine.SpyObj<MatDialog>;

  function create(project: ProjectDto, closingWith?: unknown): ComponentFixture<MissionEndActionComponent> {
    projects = jasmine.createSpyObj('ProjectService', ['endMission', 'resumeMission']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of(closingWith) } as ReturnType<MatDialog['open']>);
    TestBed.configureTestingModule({
      imports: [MissionEndActionComponent, TranslateModule.forRoot()],
      providers: [
        { provide: ProjectService, useValue: projects },
        { provide: NotificationService, useValue: notifications },
        { provide: MatDialog, useValue: dialog }
      ]
    });
    const fixture = TestBed.createComponent(MissionEndActionComponent);
    fixture.componentRef.setInput('project', project);
    fixture.detectChanges();
    return fixture;
  }

  function element(fixture: ComponentFixture<MissionEndActionComponent>, selector: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(selector);
  }

  it('offers to end a won mission only', () => {
    expect(element(create(mission), '[data-mission-end]')).not.toBeNull();
    TestBed.resetTestingModule();
    expect(element(create({ ...mission, status: ProjectStatus.OFFER }), '[data-mission-end]')).toBeNull();
  });

  it('ends the mission on the day and for the reason given, and hands it back', () => {
    const fixture = create(mission, { endedOn: '2026-10-31', reason: 'The client cut the budget' });
    const ended = { ...mission, endedOn: '2026-10-31', endReason: 'The client cut the budget' };
    projects.endMission.and.returnValue(of(ended));
    let saved: ProjectDto | undefined;
    fixture.componentInstance.changed.subscribe(project => saved = project);

    element(fixture, '[data-mission-end]')!.click();

    expect(dialog.open).toHaveBeenCalledWith(MissionEndDialogComponent, jasmine.objectContaining({
      data: { role: 'Développeur Java', startDate: '2026-01-05' }
    }));
    expect(projects.endMission).toHaveBeenCalledWith(12, '2026-10-31', 'The client cut the budget');
    expect(notifications.success).toHaveBeenCalledWith('missionEnd.endedSuccess');
    expect(saved).toEqual(ended);
  });

  it('changes nothing when the dialog is cancelled, and says when ending fails', () => {
    const cancelled = create(mission);
    element(cancelled, '[data-mission-end]')!.click();
    expect(projects.endMission).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    const failing = create(mission, { endedOn: '2026-10-31', reason: 'Done' });
    projects.endMission.and.returnValue(throwError(() => new Error('down')));
    element(failing, '[data-mission-end]')!.click();
    expect(notifications.error).toHaveBeenCalledWith('missionEnd.endError');
    expect(failing.componentInstance.busy).toBeFalse();
  });

  it('says when and why an ended mission ended, and resumes it', () => {
    const fixture = create({ ...mission, endedOn: '2026-10-31', endReason: 'The client cut the budget' });
    projects.resumeMission.and.returnValue(of(mission));

    expect(element(fixture, '[data-mission-ended]')!.textContent).toContain('The client cut the budget');
    expect(element(fixture, '[data-mission-end]')).toBeNull();
    element(fixture, '[data-mission-resume]')!.click();

    expect(projects.resumeMission).toHaveBeenCalledWith(12);
    expect(notifications.success).toHaveBeenCalledWith('missionEnd.resumedSuccess');
  });
});
