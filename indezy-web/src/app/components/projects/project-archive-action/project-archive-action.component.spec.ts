import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';
import { ProjectDto, ProjectStatus } from '../../../models';
import { ProjectService } from '../../../services/project/project.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectArchiveActionComponent } from './project-archive-action.component';
import en from '../../../../assets/i18n/en.json';

describe('ProjectArchiveActionComponent', () => {
  let projects: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const offer: ProjectDto = { id: 5, role: 'Java developer', dailyRate: 650, status: ProjectStatus.OFFER };

  function create(project: ProjectDto): ComponentFixture<ProjectArchiveActionComponent> {
    projects = jasmine.createSpyObj('ProjectService', ['archive', 'restore']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ProjectArchiveActionComponent, TranslateModule.forRoot()],
      providers: [
        { provide: ProjectService, useValue: projects },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProjectArchiveActionComponent);
    fixture.componentRef.setInput('project', project);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ProjectArchiveActionComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ').trim();
  }

  function button(fixture: ComponentFixture<ProjectArchiveActionComponent>): HTMLButtonElement {
    return (fixture.nativeElement as HTMLElement).querySelector('button')!;
  }

  it('archives an opportunity of the pipeline and hands it back', () => {
    const fixture = create(offer);
    const archived = { ...offer, archivedAt: '2026-10-26T03:30:00' };
    projects.archive.and.returnValue(of(archived));
    const changed: ProjectDto[] = [];
    fixture.componentInstance.changed.subscribe(project => changed.push(project));

    expect(text(fixture)).toBe('archive Archive');
    button(fixture).click();

    expect(projects.archive).toHaveBeenCalledWith(5);
    expect(changed).toEqual([archived]);
    expect(notifications.success).toHaveBeenCalledWith('archive.archivedSuccess');
  });

  it('says since when an opportunity is archived and restores it', () => {
    const fixture = create({ ...offer, archivedAt: '2026-10-26T03:30:00' });
    projects.restore.and.returnValue(of(offer));

    expect(text(fixture)).toContain('Archived Oct 26, 2026');
    button(fixture).click();

    expect(projects.restore).toHaveBeenCalledWith(5);
    expect(notifications.success).toHaveBeenCalledWith('archive.restoredSuccess');
  });

  it('offers nothing for a won or lost opportunity, already in the archive', () => {
    expect(text(create({ ...offer, status: ProjectStatus.WON }))).toBe('');
    TestBed.resetTestingModule();
    expect(text(create({ ...offer, status: ProjectStatus.LOST }))).toBe('');
  });

  it('says when archiving or restoring fails', () => {
    const fixture = create(offer);
    projects.archive.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.toggle();
    expect(notifications.error).toHaveBeenCalledWith('archive.archiveError');
    expect(fixture.componentInstance.busy).toBeFalse();

    fixture.componentRef.setInput('project', { ...offer, archivedAt: '2026-10-26T03:30:00' });
    projects.restore.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.toggle();
    expect(notifications.error).toHaveBeenCalledWith('archive.restoreError');
  });

  it('sends one request at a time, and none for an unsaved opportunity', () => {
    const fixture = create(offer);
    projects.archive.and.returnValue(new Subject<ProjectDto>());
    fixture.componentInstance.toggle();
    fixture.componentInstance.toggle();
    expect(projects.archive).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('project', { ...offer, id: undefined });
    fixture.componentInstance.busy = false;
    fixture.componentInstance.toggle();
    expect(projects.archive).toHaveBeenCalledTimes(1);
  });
});
