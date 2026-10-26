import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';
import { ProjectStatus, SourceDto } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { SourceService } from '../../services/source/source.service';
import { KanbanSelectionBarComponent } from './kanban-selection-bar.component';
import en from '../../../assets/i18n/en.json';

describe('KanbanSelectionBarComponent', () => {
  let projects: jasmine.SpyObj<ProjectService>;
  let sources: jasmine.SpyObj<SourceService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  const boards: SourceDto[] = [
    { id: 3, name: 'Malt', type: 'JOB_BOARD' } as SourceDto,
    { id: 2, name: 'LinkedIn', type: 'SOCIAL_MEDIA' } as SourceDto
  ];

  function create(user: { id: number } | null = { id: 7 }): ComponentFixture<KanbanSelectionBarComponent> {
    projects = jasmine.createSpyObj('ProjectService', ['bulkUpdate']);
    sources = jasmine.createSpyObj('SourceService', ['getByFreelanceId']);
    sources.getByFreelanceId.and.returnValue(of(boards));
    notifications = jasmine.createSpyObj('NotificationService', ['successText', 'error']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({
      imports: [KanbanSelectionBarComponent, TranslateModule.forRoot()],
      providers: [
        { provide: ProjectService, useValue: projects },
        { provide: SourceService, useValue: sources },
        { provide: AuthService, useValue: { getUser: () => user } },
        { provide: NotificationService, useValue: notifications },
        { provide: MatDialog, useValue: dialog }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(KanbanSelectionBarComponent);
    fixture.componentRef.setInput('projectIds', [4, 9]);
    fixture.detectChanges();
    return fixture;
  }

  it('counts the selection and lists the workspace\'s sources by name', () => {
    const fixture = create();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2 selected');
    expect(sources.getByFreelanceId).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.sources.map(source => source.name)).toEqual(['LinkedIn', 'Malt']);
  });

  it('moves the selection to a column, archives it or gives it a source, then hands over', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    projects.bulkUpdate.and.returnValue(of({ updated: 2 }));
    let done = 0;
    component.done.subscribe(() => done++);

    component.moveTo(ProjectStatus.OFFER);
    component.assignSource(3);
    component.archive();

    expect(projects.bulkUpdate.calls.allArgs()).toEqual([
      [{ action: 'STATUS', status: ProjectStatus.OFFER, projectIds: [4, 9] }],
      [{ action: 'SOURCE', sourceId: 3, projectIds: [4, 9] }],
      [{ action: 'ARCHIVE', projectIds: [4, 9] }]
    ]);
    expect(done).toBe(3);
    expect(notifications.successText).toHaveBeenCalledWith('2 opportunities updated', 3000);
  });

  it('asks why the selection was lost, and changes nothing when the question is dismissed', () => {
    const component = create().componentInstance;
    projects.bulkUpdate.and.returnValue(of({ updated: 2 }));
    dialog.open.and.returnValues(
      { afterClosed: () => of('NO_RESPONSE') } as never,
      { afterClosed: () => of(undefined) } as never);

    component.moveTo(ProjectStatus.LOST);
    component.moveTo(ProjectStatus.LOST);

    expect(dialog.open.calls.first().args[1]).toEqual(jasmine.objectContaining({ data: { role: '2 opportunities' } }));
    expect(projects.bulkUpdate).toHaveBeenCalledOnceWith({
      action: 'STATUS', status: ProjectStatus.LOST, lostReason: 'NO_RESPONSE', projectIds: [4, 9]
    });
  });

  it('says when the change fails, and sends one at a time', () => {
    const component = create().componentInstance;
    const pending = new Subject<{ updated: number }>();
    projects.bulkUpdate.and.returnValue(pending);
    component.archive();
    component.archive();
    expect(projects.bulkUpdate).toHaveBeenCalledTimes(1);
    pending.error(new Error('down'));

    expect(notifications.error).toHaveBeenCalledWith('kanban.selection.error');
    expect(component.busy).toBeFalse();
    projects.bulkUpdate.and.returnValue(throwError(() => new Error('down')));
    component.archive();
    expect(projects.bulkUpdate).toHaveBeenCalledTimes(2);
  });

  it('loads no sources without an account, nor when they fail', () => {
    expect(create(null).componentInstance.sources).toEqual([]);
    TestBed.resetTestingModule();
    projects = jasmine.createSpyObj('ProjectService', ['bulkUpdate']);
    const fixture = create();
    sources.getByFreelanceId.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.ngOnInit();
    expect(fixture.componentInstance.sources).toEqual([]);
  });

  it('asks the board to clear the selection', () => {
    const fixture = create();
    let cleared = false;
    fixture.componentInstance.clear.subscribe(() => cleared = true);

    (fixture.nativeElement.querySelector('button[aria-label="Clear the selection"]') as HTMLButtonElement).click();

    expect(cleared).toBeTrue();
  });
});
