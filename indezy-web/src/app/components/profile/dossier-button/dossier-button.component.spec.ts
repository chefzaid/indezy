import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { DossierButtonComponent } from './dossier-button.component';

describe('DossierButtonComponent', () => {
  let users: jasmine.SpyObj<UserManagementService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(): DossierButtonComponent {
    users = jasmine.createSpyObj('UserManagementService', ['downloadDossier']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [DossierButtonComponent, TranslateModule.forRoot()],
      providers: [{ provide: UserManagementService, useValue: users }, { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(DossierButtonComponent);
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
});
