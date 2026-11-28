import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { CvProfile } from '../../../models';
import { CvProfileService } from '../../../services/cv-profile/cv-profile.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { CvProfileDialogComponent } from './cv-profile-dialog.component';
import { ProfileCvProfilesComponent } from './profile-cv-profiles.component';

const BACKEND: CvProfile = { id: 3, name: 'Backend Kafka', isDefault: true, language: 'FR', headline: null, summary: null, skills: null,
  missions: null, positions: null, updatedAt: null };

describe('ProfileCvProfilesComponent', () => {
  let service: jasmine.SpyObj<CvProfileService> & { changed: Subject<void> };
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(): { component: ProfileCvProfilesComponent; element: () => HTMLElement } {
    service = Object.assign(jasmine.createSpyObj<CvProfileService>('CvProfileService', ['list', 'dossier', 'makeDefault', 'remove']),
      { changed: new Subject<void>() });
    service.list.and.returnValue(of([BACKEND]));
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as never);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [ProfileCvProfilesComponent, TranslateModule.forRoot()],
      providers: [{ provide: CvProfileService, useValue: service }, { provide: ConfirmDialogService, useValue: confirm },
        { provide: NotificationService, useValue: notifications }]
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const fixture = TestBed.createComponent(ProfileCvProfilesComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: () => {
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    } };
  }

  it('lists the profiles, the default marked, and reloads once one is saved elsewhere', () => {
    const { element } = create();
    expect(element().querySelector('[data-cv-profile="3"]')?.textContent).toContain('Backend Kafka');
    service.changed.next();
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('opens the editor and reloads once it saved', () => {
    const { component } = create();
    component.open(BACKEND);
    expect(dialog.open).toHaveBeenCalledWith(CvProfileDialogComponent, jasmine.objectContaining({ data: { profile: BACKEND } }));
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it('downloads a profile\'s dossier named after it', () => {
    const { component } = create();
    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    service.dossier.and.returnValue(of(new Blob(['%PDF'])));
    component.download(BACKEND, 'ANONYMOUS', 'DOCX');
    expect(service.dossier).toHaveBeenCalledWith(3, 'ANONYMOUS', 'DOCX');
    expect(link.download).toBe('cv-backend-kafka-anonymous.docx');
    service.dossier.and.returnValue(throwError(() => new Error('down')));
    component.download(BACKEND, 'NAMED', 'PDF');
    expect(notifications.error).toHaveBeenCalledWith('profile.dossier.error');
  });

  it('makes one the default, and deletes one once confirmed', () => {
    const { component } = create();
    service.makeDefault.and.returnValue(of(BACKEND));
    component.makeDefault(BACKEND);
    expect(service.makeDefault).toHaveBeenCalledWith(3);
    confirm.confirm.and.returnValue(of(false));
    component.remove(BACKEND);
    expect(service.remove).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    service.remove.and.returnValue(of(undefined));
    component.remove(BACKEND);
    expect(service.remove).toHaveBeenCalledWith(3);
  });
});
