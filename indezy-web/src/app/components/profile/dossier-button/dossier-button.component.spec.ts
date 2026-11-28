import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { AiCvRewrite } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { AiCvRewriteDialogComponent, CvReviewData } from '../../../shared/components/ai-cv-rewrite-dialog/ai-cv-rewrite-dialog.component';
import { DossierButtonComponent } from './dossier-button.component';
import { PostingDossierDialogComponent } from './posting-dossier-dialog.component';

const REWRITE = { headline: { before: null, after: null }, summary: { before: null, after: null }, skillsBefore: [], skills: [],
  missions: [], positions: [] } as AiCvRewrite;

describe('DossierButtonComponent', () => {
  let users: jasmine.SpyObj<UserManagementService>;
  let ai: jasmine.SpyObj<AiService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialog: jasmine.SpyObj<MatDialog>;

  function create(projectId: number | null = null, offerId: number | null = null, closedWith: unknown = undefined): DossierButtonComponent {
    users = jasmine.createSpyObj('UserManagementService', ['downloadDossier']);
    ai = jasmine.createSpyObj('AiService', ['reviewDossier', 'reviewOfferDossier']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of(closedWith) } as never);
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

  function reviewData(): CvReviewData {
    const [component, config] = dialog.open.calls.mostRecent().args;
    expect(component).toBe(AiCvRewriteDialogComponent);
    return config!.data as CvReviewData;
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

  it('has the AI tailor the dossier to an opportunity, then opens it to review', () => {
    const component = create(7);
    ai.reviewDossier.and.returnValue(of(REWRITE));
    component.tailor('EN');
    expect(ai.reviewDossier).toHaveBeenCalledWith(7, 'EN');
    expect(reviewData().tailored).toEqual({ rewrite: REWRITE, language: 'EN' });
    expect(component.busy).toBeFalse();
  });

  it('tailors it to a feed offer and hands on each file downloaded from the review', () => {
    const component = create(null, 5);
    ai.reviewOfferDossier.and.returnValue(of(REWRITE));
    const made = jasmine.createSpy('made');
    component.made.subscribe(made);
    component.tailor('FR');
    expect(ai.reviewOfferDossier).toHaveBeenCalledWith(5, 'FR');
    const file = new File(['%PDF'], 'dossier-competences-tailored.pdf');
    reviewData().made!(file);
    expect(made).toHaveBeenCalledWith(file);
  });

  it('says why the AI could not tailor the dossier', () => {
    const component = create(7);
    ai.reviewDossier.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'The AI declined this request' } })));
    component.tailor('FR');
    expect(component.busy).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.declined');
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('reviews the dossier tailored to a pasted posting once its dialog gives it back', () => {
    const component = create(null, null, { rewrite: REWRITE, language: 'FR' });
    component.tailorToPosting();
    expect(dialog.open.calls.first().args[0]).toBe(PostingDossierDialogComponent);
    expect(reviewData().tailored).toEqual({ rewrite: REWRITE, language: 'FR' });
  });

  it('reviews nothing when the posting dialog was closed', () => {
    const component = create();
    component.tailorToPosting();
    expect(dialog.open).toHaveBeenCalledTimes(1);
  });
});
