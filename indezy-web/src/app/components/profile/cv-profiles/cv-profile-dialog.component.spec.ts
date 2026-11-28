import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { CvProfile, CvProfileSources } from '../../../models';
import { CvProfileService } from '../../../services/cv-profile/cv-profile.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { CvProfileDialogComponent } from './cv-profile-dialog.component';

const SOURCES: CvProfileSources = { headline: 'Développeuse Java', summary: 'Dix ans de Java.', skills: ['Java', 'Kafka', 'Angular'],
  missions: [{ id: 10, title: 'Lead Kafka', description: 'Flux.' }, { id: 11, title: 'Dev Angular', description: 'Front.' }],
  positions: [{ id: 20, title: 'Développeuse', description: 'Paie.' }] };

describe('CvProfileDialogComponent', () => {
  let service: jasmine.SpyObj<CvProfileService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<CvProfileDialogComponent, boolean>>;

  function create(profile?: CvProfile): CvProfileDialogComponent {
    service = jasmine.createSpyObj('CvProfileService', ['sources', 'create', 'update', 'preview']);
    service.sources.and.returnValue(of(SOURCES));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [CvProfileDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: CvProfileService, useValue: service }, { provide: NotificationService, useValue: notifications },
        { provide: MatDialogRef, useValue: dialogRef }, { provide: MAT_DIALOG_DATA, useValue: { profile } }]
    });
    const fixture = TestBed.createComponent(CvProfileDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function rows(component: CvProfileDialogComponent, key: string) {
    return component.groups.find(group => group.key === key)!.rows;
  }

  it('starts from the profile as it is: everything shown, nothing set', () => {
    const component = create();
    component.name = ' Backend ';
    expect(component.details()).toEqual({ name: 'Backend', language: 'FR', headline: null, summary: null, skills: null, missions: null, positions: null,
      style: 'CLASSIC', accent: null });
  });

  it('keeps what is left out, the order and the wording chosen', () => {
    const component = create();
    component.name = 'Backend';
    rows(component, 'skills')[2].shown = false;
    component.move(rows(component, 'skills'), 1, -1);
    rows(component, 'missions')[1].shown = false;
    rows(component, 'missions')[0].text = ' Flux de paie. ';

    expect(component.details()).toEqual(jasmine.objectContaining({ skills: ['Kafka', 'Java'], missions: [{ id: 10, text: 'Flux de paie.' }],
      positions: null }));
    service.create.and.returnValue(of({} as CvProfile));
    component.save();
    expect(service.create).toHaveBeenCalled();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('shows a profile\'s choices first, in its order, the rest unticked, and saves it in place', () => {
    const component = create({ id: 3, name: 'Front', isDefault: false, language: 'EN', headline: 'Angular', summary: null, skills: ['Angular'],
      missions: [{ id: 11, text: 'Front Angular.' }], positions: null, updatedAt: null });
    expect(rows(component, 'skills').map(row => [row.label, row.shown])).toEqual([['Angular', true], ['Java', false], ['Kafka', false]]);
    expect(rows(component, 'missions').map(row => [row.id, row.shown, row.text])).toEqual([[11, true, 'Front Angular.'], [10, false, '']]);
    service.update.and.returnValue(throwError(() => new Error('down')));
    component.save();
    expect(service.update).toHaveBeenCalledWith(3, jasmine.objectContaining({ name: 'Front', language: 'EN', headline: 'Angular' }));
    expect(notifications.error).toHaveBeenCalledWith('cvProfiles.saveError');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('closes when the profile cannot be read', () => {
    service = jasmine.createSpyObj('CvProfileService', ['sources']);
    TestBed.configureTestingModule({
      imports: [CvProfileDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: CvProfileService, useValue: { sources: () => throwError(() => new Error('down')) } },
        { provide: NotificationService, useValue: jasmine.createSpyObj('NotificationService', ['error']) },
        { provide: MatDialogRef, useValue: jasmine.createSpyObj('MatDialogRef', ['close']) }, { provide: MAT_DIALOG_DATA, useValue: null }]
    });
    const fixture = TestBed.createComponent(CvProfileDialogComponent);
    fixture.detectChanges();
    expect(TestBed.inject(MatDialogRef).close).toHaveBeenCalledWith(false);
  });

  it('previews the dossier as set now, in the layout and colour chosen, before saving', () => {
    const component = create();
    spyOn(URL, 'createObjectURL').and.returnValue('blob:preview');
    const revoke = spyOn(URL, 'revokeObjectURL');
    service.preview.and.returnValue(of(new Blob(['png'])));
    component.name = 'Backend';
    component.style = 'COMPACT';
    component.accent = '#1A73E8';
    component.preview();
    expect(service.preview).toHaveBeenCalledWith(jasmine.objectContaining({ name: 'Backend', style: 'COMPACT', accent: '#1A73E8' }), 'NAMED');
    expect(component.previewUrl).not.toBeNull();
    component.preview();
    expect(revoke).toHaveBeenCalledWith('blob:preview');
    service.preview.and.returnValue(throwError(() => new Error('down')));
    component.preview();
    expect(notifications.error).toHaveBeenCalledWith('cvProfiles.previewError');
    component.ngOnDestroy();
  });
});
