import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { DossierButtonComponent } from './dossier-button.component';

describe('DossierButtonComponent', () => {
  let users: jasmine.SpyObj<UserManagementService>;
  let ai: jasmine.SpyObj<AiService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(projectId: number | null = null): DossierButtonComponent {
    users = jasmine.createSpyObj('UserManagementService', ['downloadDossier']);
    ai = jasmine.createSpyObj('AiService', ['tailoredDossier']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [DossierButtonComponent, TranslateModule.forRoot()],
      providers: [{ provide: UserManagementService, useValue: users }, { provide: AiService, useValue: ai },
        { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(DossierButtonComponent);
    fixture.componentInstance.projectId = projectId;
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
});
