import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ClientDto, Offer, OfferLink, ProjectDto, ProjectStatus } from '../../../models';
import { AuthService } from '../../../services/auth/auth.service';
import { ClientService } from '../../../services/client/client.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { ProjectService } from '../../../services/project/project.service';
import { SeasonService } from '../../../services/season/season.service';
import { SourceService } from '../../../services/source/source.service';
import { NEW_RECORD, ProjectFormComponent } from './project-form.component';

describe('ProjectFormComponent', () => {
  const clients = [
    { id: 1, companyName: 'Société Générale', city: 'Paris', isFinal: true },
    { id: 2, companyName: 'Exemple Conseil', city: 'Lyon', isFinal: false },
    { id: 3, companyName: 'Airbus', city: 'Toulouse', isFinal: true }
  ] as ClientDto[];
  let projects: jasmine.SpyObj<ProjectService>;
  let offers: jasmine.SpyObj<OfferService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let navigate: jasmine.Spy;

  function create(id?: number, query: Record<string, string> = {}): ComponentFixture<ProjectFormComponent> {
    projects = jasmine.createSpyObj('ProjectService', ['getById', 'create', 'update']);
    offers = jasmine.createSpyObj('OfferService', ['resolveLink', 'importOffer', 'importPosting']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ProjectFormComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNativeDateAdapter(),
        { provide: ActivatedRoute, useValue: { params: of(id ? { id } : {}), snapshot: { queryParamMap: convertToParamMap(query) } } },
        { provide: ProjectService, useValue: projects },
        { provide: OfferService, useValue: offers },
        { provide: ClientService, useValue: { getByFreelanceId: () => of(clients) } },
        { provide: SourceService, useValue: { getByFreelanceId: () => of([]) } },
        { provide: SeasonService, useValue: { getByFreelanceId: () => of([{ id: 4, name: 'Automne', startDate: '2026-09-01', active: true }]) } },
        { provide: AuthService, useValue: { getUser: () => ({ id: 7 }) } },
        { provide: NotificationService, useValue: notifications }
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

  describe('from an offer link', () => {
    const offer = { id: 7, title: 'Développeur Java', siteName: 'Free-Work' } as Offer;
    const link: OfferLink = {
      status: 'FOUND', siteName: 'Free-Work', offer,
      draft: {
        project: { role: 'Développeur Java', dailyRate: 650, techStack: 'Java, Spring', workMode: 'HYBRID', remoteDaysPerMonth: 9,
          startDate: '2026-11-02', durationInMonths: 6, link: 'https://www.free-work.com/fr/mission/7', status: ProjectStatus.CONTACT },
        clientName: 'Nouvelle ESN', sourceName: 'Free-Work'
      }
    };

    function fill(query: Record<string, string> = {}): ComponentFixture<ProjectFormComponent> {
      const fixture = create(undefined, query);
      fixture.detectChanges();
      fixture.componentInstance.fillFromOffer(link);
      fixture.detectChanges();
      return fixture;
    }

    it('offers the field on a new opportunity only, focused when opened to start from a link', () => {
      const fresh = create(undefined, { from: 'link' });
      fresh.detectChanges();
      expect(fresh.componentInstance.startFromLink).toBeTrue();
      expect(fresh.nativeElement.querySelector('app-offer-link-field')).not.toBeNull();

      TestBed.resetTestingModule();
      const editing = create(40);
      projects.getById.and.returnValue(of({ id: 40, role: 'Développeur Java', dailyRate: 600, clientId: 1 } as ProjectDto));
      editing.detectChanges();
      expect(editing.nativeElement.querySelector('app-offer-link-field')).toBeNull();
    });

    it('fills the form in, keeping the stage chosen, and names the ESN to create', () => {
      const fixture = fill({ status: ProjectStatus.INTERVIEW });
      const form = fixture.componentInstance.projectForm.value;

      expect(form).toEqual(jasmine.objectContaining({
        role: 'Développeur Java', dailyRate: 650, techStack: 'Java, Spring', workMode: 'HYBRID', remoteDaysPerMonth: 9,
        durationInMonths: 6, link: 'https://www.free-work.com/fr/mission/7', clientId: NEW_RECORD, sourceId: NEW_RECORD,
        status: ProjectStatus.INTERVIEW
      }));
      expect(fixture.nativeElement.querySelector('[data-from-offer]').textContent).toContain('projects.offerLink.linked');
      expect(fixture.componentInstance.newClientName).toBe('Nouvelle ESN');
    });

    it('adds the offer to the pipeline as corrected, its new client and source made on the server', () => {
      const fixture = fill();
      offers.importOffer.and.returnValue(of({ id: 42 } as ProjectDto));
      fixture.componentInstance.projectForm.patchValue({ role: 'Tech lead Java' });

      fixture.componentInstance.onSubmit();

      expect(projects.create).not.toHaveBeenCalled();
      const [offerId, sent] = offers.importOffer.calls.mostRecent().args;
      expect(offerId).toBe(7);
      expect(sent).toEqual(jasmine.objectContaining({ role: 'Tech lead Java', clientId: undefined, sourceId: undefined, freelanceId: 7 }));
      expect(navigate).toHaveBeenCalledWith(['/projects', 42]);
    });

    it('keeps a client and source the workspace has', () => {
      const fixture = create();
      fixture.detectChanges();
      fixture.componentInstance.fillFromOffer({ ...link, draft: { ...link.draft!, project: { ...link.draft!.project, clientId: 2, sourceId: 5 } } });
      offers.importOffer.and.returnValue(of({ id: 42 } as ProjectDto));

      fixture.componentInstance.onSubmit();

      expect(fixture.componentInstance.newClientName).toBeUndefined();
      expect(offers.importOffer.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({ clientId: 2, sourceId: 5 }));
    });

    it('says when the mission joined the pipeline meanwhile', () => {
      spyOn(console, 'error');
      const fixture = fill();
      offers.importOffer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

      fixture.componentInstance.onSubmit();

      expect(notifications.error).toHaveBeenCalledWith('projects.offerLink.alreadyInPipeline');
      expect(fixture.componentInstance.isSubmitting).toBeFalse();
    });

    it('adds a posting of another site as corrected, with the client and source its draft names', () => {
      const fixture = create();
      fixture.detectChanges();
      const posting: OfferLink = { status: 'POSTING', siteName: 'acme-careers.fr', draft: {
        project: { role: 'Développeur Java', dailyRate: 600, link: 'https://www.acme-careers.fr/postes/42', status: ProjectStatus.CONTACT },
        clientName: 'Acme', sourceName: 'acme-careers.fr', sourceLink: 'https://www.acme-careers.fr' } };
      fixture.componentInstance.fillFromOffer(posting);
      fixture.detectChanges();
      offers.importPosting.and.returnValue(of({ id: 44 } as ProjectDto));

      expect(fixture.nativeElement.querySelector('[data-from-offer]').textContent).toContain('projects.offerLink.linked');
      expect(fixture.componentInstance.newClientName).toBe('Acme');
      fixture.componentInstance.onSubmit();

      expect(offers.importOffer).not.toHaveBeenCalled();
      expect(projects.create).not.toHaveBeenCalled();
      const [sent] = offers.importPosting.calls.mostRecent().args;
      expect(sent).toEqual(jasmine.objectContaining({ clientName: 'Acme', sourceName: 'acme-careers.fr', sourceLink: 'https://www.acme-careers.fr' }));
      expect(sent.project).toEqual(jasmine.objectContaining({ role: 'Développeur Java', clientId: undefined, sourceId: undefined }));
      expect(navigate).toHaveBeenCalledWith(['/projects', 44]);
      // A link that gave nothing to fill in leaves the form as it is.
      fixture.componentInstance.fillFromOffer({ status: 'GONE', siteName: 'acme-careers.fr' });
      expect(fixture.componentInstance.offerLink).toBe(posting);
    });

    it('keeps what was filled in as an opportunity of its own once unlinked', () => {
      const fixture = fill();
      fixture.componentInstance.unlinkOffer();
      fixture.detectChanges();
      fixture.componentInstance.projectForm.patchValue({ clientId: 3 });
      projects.create.and.returnValue(of({ id: 43 } as ProjectDto));

      fixture.componentInstance.onSubmit();

      expect(fixture.nativeElement.querySelector('[data-from-offer]')).toBeNull();
      expect(offers.importOffer).not.toHaveBeenCalled();
      expect(projects.create).toHaveBeenCalledWith(jasmine.objectContaining({ role: 'Développeur Java', clientId: 3 }));
    });

    it('clears the client and source to create when unlinked, and ignores a link without a draft', () => {
      const fixture = fill();

      fixture.componentInstance.unlinkOffer();

      expect(fixture.componentInstance.projectForm.value.clientId).toBeNull();
      expect(fixture.componentInstance.projectForm.value.sourceId).toBeNull();
      fixture.componentInstance.fillFromOffer({ status: 'FOUND' });
      expect(fixture.componentInstance.offerLink).toBeUndefined();
    });
  });
});
