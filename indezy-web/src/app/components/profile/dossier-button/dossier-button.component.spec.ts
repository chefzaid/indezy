import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { MatDialog } from '@angular/material/dialog';
import { DossierButtonComponent } from './dossier-button.component';
import { PostingDossierDialogComponent } from './posting-dossier-dialog.component';

describe('DossierButtonComponent', () => {
  let users: jasmine.SpyObj<UserManagementService>;
  let ai: jasmine.SpyObj<AiService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  let dialog: jasmine.SpyObj<MatDialog>;

  function create(projectId: number | null = null, offerId: number | null = null): DossierButtonComponent {
    users = jasmine.createSpyObj('UserManagementService', ['downloadDossier']);
    ai = jasmine.createSpyObj('AiService', ['tailoredDossier', 'offerDossier']);
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [DossierButtonComponent, TranslateModule.forRoot()],
      providers: [{ provide: UserManagementService, useValue: users }, { provide: AiService, useValue: ai },
        { provide: NotificationService, useValue: notifications }]
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const fixture = TestBed.createComponent(DossierButtonComponent);
    fixture.componentInstance.projectId = projectId;
    fixture.componentInstance.offerId = offerId;
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('downloads the dossier chosen, named after its language and layout', () => {
    const component = create();
    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    users.downloadDossier.and.returnValue(of(new Blob(['%PDF'])));

    expect(component.choices.length).toBe(4);
    component.download('ANONYMOUS', 'EN');
    expect(users.downloadDossier).toHaveBeenCalledWith('ANONYMOUS', 'EN');
    expect(link.download).toBe('skills-profile-anonymous.pdf');
    component.download('NAMED', 'FR');
    expect(link.download).toBe('dossier-competences.pdf');
  });

  it('says when the dossier cannot be made', () => {
    const component = create();
    users.downloadDossier.and.returnValue(throwError(() => new Error('down')));
    component.download('NAMED', 'FR');
    expect(notifications.error).toHaveBeenCalledWith('profile.dossier.error');
  });

  it('asks the AI for the dossier tailored to an opportunity', () => {
    const component = create(7);
    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    ai.tailoredDossier.and.returnValue(of(new Blob(['%PDF'])));

    component.download('NAMED', 'EN');
    expect(ai.tailoredDossier).toHaveBeenCalledWith(7, 'NAMED', 'EN');
    expect(users.downloadDossier).not.toHaveBeenCalled();
    expect(link.download).toBe('skills-profile-tailored.pdf');
    expect(component.busy).toBeFalse();
  });

  it('says why the AI could not tailor the dossier', async () => {
    const component = create(7);
    const error = new HttpErrorResponse({ status: 409, error: new Blob(['{"message":"The AI declined this request"}']) });
    ai.tailoredDossier.and.returnValue(throwError(() => error));
    component.download('ANONYMOUS', 'FR');
    expect(component.busy).toBeFalse();
    await new Promise(resolve => setTimeout(resolve, 20));
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.declined');
  });

  it('tailors the dossier to a feed offer and hands the file on', () => {
    const component = create(null, 5);
    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    ai.offerDossier.and.returnValue(of(new Blob(['%PDF'])));
    const made = jasmine.createSpy('made');
    component.made.subscribe(made);

    component.download('ANONYMOUS', 'FR');
    expect(ai.offerDossier).toHaveBeenCalledWith(5, 'ANONYMOUS', 'FR');
    expect(link.download).toBe('dossier-competences-anonymous-tailored.pdf');
    expect((made.calls.mostRecent().args[0] as File).name).toBe('dossier-competences-anonymous-tailored.pdf');
  });

  it('hands on no file for the profile\'s own dossier, and offers to tailor it to a pasted posting', () => {
    const component = create();
    users.downloadDossier.and.returnValue(of(new Blob(['%PDF'])));
    spyOn(HTMLAnchorElement.prototype, 'click');
    const made = jasmine.createSpy('made');
    component.made.subscribe(made);
    component.download('NAMED', 'FR');
    expect(made).not.toHaveBeenCalled();
    component.tailorToPosting();
    expect(dialog.open).toHaveBeenCalledWith(PostingDossierDialogComponent, jasmine.any(Object));
  });
});
