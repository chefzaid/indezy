import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { ClientDto, ProjectDto, ProjectStatus } from '../../../models';
import { AuthService } from '../../../services/auth/auth.service';
import { ClientService } from '../../../services/client/client.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { SeasonService } from '../../../services/season/season.service';
import { SourceService } from '../../../services/source/source.service';
import { ProjectFormComponent } from './project-form.component';

describe('ProjectFormComponent', () => {
  const clients = [
    { id: 1, companyName: 'Société Générale', city: 'Paris', isFinal: true },
    { id: 2, companyName: 'Exemple Conseil', city: 'Lyon', isFinal: false },
    { id: 3, companyName: 'Airbus', city: 'Toulouse', isFinal: true }
  ] as ClientDto[];
  let projects: jasmine.SpyObj<ProjectService>;
  let navigate: jasmine.Spy;

  function create(id?: number): ComponentFixture<ProjectFormComponent> {
    projects = jasmine.createSpyObj('ProjectService', ['getById', 'create', 'update']);
    TestBed.configureTestingModule({
      imports: [ProjectFormComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNativeDateAdapter(),
        { provide: ActivatedRoute, useValue: { params: of(id ? { id } : {}), snapshot: { queryParamMap: convertToParamMap({}) } } },
        { provide: ProjectService, useValue: projects },
        { provide: ClientService, useValue: { getByFreelanceId: () => of(clients) } },
        { provide: SourceService, useValue: { getByFreelanceId: () => of([]) } },
        { provide: SeasonService, useValue: { getByFreelanceId: () => of([{ id: 4, name: 'Automne', startDate: '2026-09-01', active: true }]) } },
        { provide: AuthService, useValue: { getUser: () => ({ id: 7 }) } },
        { provide: NotificationService, useValue: jasmine.createSpyObj('NotificationService', ['success', 'error']) }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate');
    return TestBed.createComponent(ProjectFormComponent);
  }

  function clientField(fixture: ComponentFixture<ProjectFormComponent>): HTMLElement {
    return fixture.nativeElement.querySelector('mat-select[formControlName="clientId"]');
  }

  it('shows an ESN that is the client of an opportunity added from the offer feed', async () => {
    const fixture = create(40);
    projects.getById.and.returnValue(of({ id: 40, role: 'Développeur Java', dailyRate: 600, clientId: 2, freelanceId: 7 } as ProjectDto));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(clientField(fixture).textContent?.trim()).toBe('Exemple Conseil');
    expect(fixture.componentInstance.clients.map(client => client.companyName)).toEqual(['Airbus', 'Société Générale']);
    expect(fixture.componentInstance.intermediaries.map(client => client.companyName)).toEqual(['Exemple Conseil']);
  });

  it('creates an opportunity in the running season and opens it', () => {
    const fixture = create();
    projects.create.and.returnValue(of({ id: 41 } as ProjectDto));
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.projectForm.patchValue({ role: 'Architecte cloud', dailyRate: 700, clientId: 3 });

    component.onSubmit();

    expect(projects.create).toHaveBeenCalledWith(jasmine.objectContaining({
      role: 'Architecte cloud', dailyRate: 700, clientId: 3, seasonId: 4, status: ProjectStatus.CONTACT, freelanceId: 7
    }));
    expect(navigate).toHaveBeenCalledWith(['/projects', 41]);
  });
});
