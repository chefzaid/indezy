import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { ProfileCv } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProfileCvService } from '../../../services/profile-cv/profile-cv.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ProfileCvCardComponent } from './profile-cv-card.component';

describe('ProfileCvCardComponent', () => {
  let cvs: jasmine.SpyObj<ProfileCvService> & { changed: Subject<void> };
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const kept: ProfileCv = { fileName: 'cv-alice.pdf', contentType: 'application/pdf', sizeBytes: 1200, keptAt: '2026-11-23T10:00:00' };

  function create(current: ProfileCv | null): { component: ProfileCvCardComponent; element: () => HTMLElement } {
    cvs = Object.assign(jasmine.createSpyObj('ProfileCvService', ['info', 'keep', 'download', 'remove']), { changed: new Subject<void>() });
    cvs.info.and.returnValue(of(current));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [ProfileCvCardComponent, TranslateModule.forRoot()],
      providers: [{ provide: ProfileCvService, useValue: cvs }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }]
    });
    const fixture = TestBed.createComponent(ProfileCvCardComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: () => {
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    } };
  }

  function picked(name: string): Event {
    const input = document.createElement('input');
    input.type = 'file';
    const files = new DataTransfer();
    files.items.add(new File(['%PDF'], name));
    input.files = files.files;
    return { target: input } as unknown as Event;
  }

  it('keeps a CV, saying when it is refused', () => {
    const { component, element } = create(null);
    expect(element().querySelector('[data-profile-cv-add]')).not.toBeNull();
    cvs.keep.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    component.keep(picked('notes.txt'));
    expect(notifications.error).toHaveBeenCalledWith('profileCv.refused');
    cvs.keep.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.keep(picked('cv.pdf'));
    expect(notifications.error).toHaveBeenCalledWith('profileCv.keepError');
    cvs.keep.and.returnValue(of(kept));
    component.keep(picked('cv.pdf'));
    expect(notifications.success).toHaveBeenCalledWith('profileCv.kept');
    expect(element().querySelector('[data-profile-cv-kept]')?.textContent).toContain('cv-alice.pdf');
    // No file chosen: nothing is kept.
    component.keep({ target: document.createElement('input') } as unknown as Event);
    expect(cvs.keep).toHaveBeenCalledTimes(3);
  });

  it('downloads and removes the CV kept, and reads it again once the import keeps another', () => {
    const { component, element } = create(kept);
    cvs.download.and.returnValue(throwError(() => new Error('down')));
    component.download();
    expect(notifications.error).toHaveBeenCalledWith('profileCv.downloadError');

    confirm.confirm.and.returnValue(of(true));
    cvs.remove.and.returnValue(of(undefined));
    component.remove();
    expect(component.cv).toBeNull();
    expect(element().querySelector('[data-profile-cv-add]')).not.toBeNull();

    cvs.info.and.returnValue(of(kept));
    cvs.changed.next();
    expect(component.cv).toEqual(kept);
    cvs.info.and.returnValue(throwError(() => new Error('down')));
    component.reload();
    expect(component.cv).toBeNull();
  });
});
