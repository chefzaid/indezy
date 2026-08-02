import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ArchiveComponent } from './archive.component';
import { ProjectService } from '../../services/project/project.service';
import { AuthService } from '../../services/auth/auth.service';
import { ProjectDto, ProjectStatus } from '../../models';
import { User } from '../../models/auth.models';

describe('ArchiveComponent', () => {
  let fixture: ComponentFixture<ArchiveComponent>;
  let component: ArchiveComponent;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const project = (over: Partial<ProjectDto>): ProjectDto =>
    ({ role: 'Dev', dailyRate: 500, ...over } as ProjectDto);

  const projects: ProjectDto[] = [
    project({ id: 1, status: ProjectStatus.WON, role: 'Angular Lead', startDate: '2025-01-01', techStack: 'Angular' }),
    project({ id: 2, status: ProjectStatus.LOST, role: 'Java Dev', startDate: '2025-06-01', clientName: 'Acme' }),
    project({ id: 3, status: ProjectStatus.IDENTIFIED, role: 'Open Role', startDate: '2025-03-01' })
  ];

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj<ProjectService>('ProjectService', ['getByFreelanceId']);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['getUser']);

    await TestBed.configureTestingModule({
      imports: [ArchiveComponent, RouterModule.forRoot([]), TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: AuthService, useValue: authServiceSpy }
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
});
