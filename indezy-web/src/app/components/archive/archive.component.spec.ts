import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ArchiveComponent } from './archive.component';
import { ProjectService } from '../../services/project/project.service';
import { AuthService } from '../../services/auth/auth.service';
import { ProjectDto, ProjectStatus } from '../../models';
import { User } from '../../models/auth.models';
import { NotificationService } from '../../services/notification/notification.service';

describe('ArchiveComponent', () => {
  let fixture: ComponentFixture<ArchiveComponent>;
  let component: ArchiveComponent;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  const project = (over: Partial<ProjectDto>): ProjectDto =>
    ({ role: 'Dev', dailyRate: 500, ...over } as ProjectDto);

  const projects: ProjectDto[] = [
    project({ id: 1, status: ProjectStatus.WON, role: 'Angular Lead', startDate: '2025-01-01', techStack: 'Angular' }),
    project({ id: 2, status: ProjectStatus.LOST, role: 'Java Dev', startDate: '2025-06-01', clientName: 'Acme' }),
    project({ id: 3, status: ProjectStatus.CONTACT, role: 'Open Role', startDate: '2025-03-01' })
  ];

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj<ProjectService>('ProjectService', ['getByFreelanceId', 'restore']);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['getUser']);
    notifications = jasmine.createSpyObj<NotificationService>('NotificationService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [ArchiveComponent, RouterModule.forRoot([]), TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        { provide: NotificationService, useValue: notifications }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ArchiveComponent);
    component = fixture.componentInstance;
  });

  it('shows only closed opportunities, most recent first', () => {
    authServiceSpy.getUser.and.returnValue({ id: 7 } as User);
    projectServiceSpy.getByFreelanceId.and.returnValue(of(projects));

    fixture.detectChanges();

    expect(projectServiceSpy.getByFreelanceId).toHaveBeenCalledWith(7);
    expect(component.filteredProjects.map(p => p.id)).toEqual([2, 1]);
  });

  it('filters by a full-text query across fields', () => {
    const archived = component.toArchived(projects);

    expect(component.matchQuery(archived, 'acme').map(p => p.id)).toEqual([2]);
    expect(component.matchQuery(archived, 'angular').map(p => p.id)).toEqual([1]);
    expect(component.matchQuery(archived, '').map(p => p.id)).toEqual([2, 1]);
  });

  it('does not query when no user is authenticated', () => {
    authServiceSpy.getUser.and.returnValue(null);

    fixture.detectChanges();

    expect(projectServiceSpy.getByFreelanceId).not.toHaveBeenCalled();
  });

  describe('archived opportunities', () => {
    const idle = project({ id: 4, status: ProjectStatus.OFFER, role: 'Idle offer', startDate: '2024-01-01', archivedAt: '2025-09-01T03:30:00' });

    function show(list: ProjectDto[]): void {
      authServiceSpy.getUser.and.returnValue({ id: 7 } as User);
      projectServiceSpy.getByFreelanceId.and.returnValue(of(list));
      fixture.detectChanges();
    }

    it('lists them with the closed ones, by when they were archived, with a way back', () => {
      show([...projects, idle]);

      expect(component.filteredProjects.map(p => p.id)).toEqual([4, 2, 1]);
      const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('.archive-archived button');
      expect(buttons.length).toBe(1);
    });

    it('restores one to its pipeline column, off the list', () => {
      show([...projects, idle]);
      projectServiceSpy.restore.and.returnValue(of({ ...idle, archivedAt: undefined }));
      const event = new MouseEvent('click');
      const stop = spyOn(event, 'stopPropagation');

      component.restore(idle, event);

      expect(stop).toHaveBeenCalled();
      expect(projectServiceSpy.restore).toHaveBeenCalledWith(4);
      expect(component.filteredProjects.map(p => p.id)).toEqual([2, 1]);
      expect(notifications.success).toHaveBeenCalledWith('archive.restoredSuccess');
    });

    it('keeps one that could not be restored and says so', () => {
      show([idle]);
      projectServiceSpy.restore.and.returnValue(throwError(() => new Error('down')));

      component.restore(idle, new MouseEvent('click'));

      expect(component.filteredProjects.map(p => p.id)).toEqual([4]);
      expect(notifications.error).toHaveBeenCalledWith('archive.restoreError');
    });
  });
});
