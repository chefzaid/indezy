import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';

import { NetworkFile, UploadedFile } from '../../../models';
import { NetworkFilesService } from '../../../services/network/network-files.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { FilePickerComponent } from './file-picker.component';
import en from '../../../../assets/i18n/en.json';

describe('FilePickerComponent', () => {
  let api: jasmine.SpyObj<NetworkFilesService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let fixture: ComponentFixture<FilePickerComponent>;
  let changes: number;
  const uploaded = (id: number, kind: NetworkFile['kind'], confidential: UploadedFile['confidential'] = []): UploadedFile => ({
    file: { id, kind, name: `f${id}`, contentType: kind === 'IMAGE' ? 'image/jpeg' : 'application/pdf', size: 1024, alt: null, width: null,
      height: null, pages: null, url: `/public/network-files/${id}/full`, previewUrl: null }, confidential });

  function create(): HTMLElement {
    api = jasmine.createSpyObj('NetworkFilesService', ['upload', 'discard']);
    api.discard.and.returnValue(of(undefined));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [FilePickerComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: NetworkFilesService, useValue: api },
        { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    fixture = TestBed.createComponent(FilePickerComponent);
    changes = 0;
    fixture.componentInstance.changed.subscribe(() => changes++);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  /** Chooses the files in the image or the document input. */
  function choose(element: HTMLElement, files: File[], images: boolean): void {
    const input = element.querySelector(images ? '[data-pick-images-input]' : '[data-pick-documents-input]') as HTMLInputElement;
    const list = new DataTransfer();
    files.forEach(file => list.items.add(file));
    input.files = list.files;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  const file = (name: string, size = 10): File => {
    const made = new File([new Uint8Array(10)], name);
    Object.defineProperty(made, 'size', { value: size });
    return made;
  };

  it('uploads images at once, each needing a description before the post or the message goes', () => {
    const element = create();
    const pending = new Subject<UploadedFile>();
    api.upload.and.returnValues(pending.asObservable(), of(uploaded(2, 'IMAGE')));
    choose(element, [file('a.jpg'), file('b.png')], true);
    const component = fixture.componentInstance;
    expect(component.images.length).toBe(2);
    expect(component.uploading).toBeTrue();
    expect(component.valid).toBeFalse();
    expect(element.querySelector('mat-spinner')).not.toBeNull();
    pending.next(uploaded(1, 'IMAGE'));
    fixture.detectChanges();
    expect(component.uploading).toBeFalse();
    expect(component.valid).toBeFalse();
    const alts = element.querySelectorAll('[data-picked-alt]') as NodeListOf<HTMLInputElement>;
    expect(alts[0].getAttribute('aria-invalid')).toBe('true');
    component.picked[0].alt.setValue(' Le tableau ');
    component.picked[1].alt.setValue('Le schéma');
    expect(component.valid).toBeTrue();
    expect(component.refs()).toEqual([{ id: 1, alt: 'Le tableau' }, { id: 2, alt: 'Le schéma' }]);
    expect(changes).toBeGreaterThan(3);

    (element.querySelector('[data-picked-remove]') as HTMLButtonElement).click();
    expect(api.discard).toHaveBeenCalledWith(1);
    expect(component.refs()).toEqual([{ id: 2, alt: 'Le schéma' }]);
    component.clear();
    expect(component.picked).toEqual([]);
  });

  it('says what it leaves out: beyond the limits, beyond 50 MB, refused, or too many today', () => {
    const element = create();
    api.upload.and.returnValue(of(uploaded(1, 'IMAGE')));
    choose(element, [1, 2, 3, 4, 5].map(i => file(`p${i}.png`)), true);
    expect(notifications.error).toHaveBeenCalledWith('network.files.tooManyImages');
    expect(fixture.componentInstance.images.length).toBe(4);
    expect((element.querySelector('[data-pick-images]') as HTMLButtonElement).disabled).toBeTrue();
    fixture.componentInstance.clear();
    choose(element, [file('big.pdf', 50 * 1024 * 1024 + 1)], false);
    expect(notifications.error).toHaveBeenCalledWith('network.files.tooLarge');
    api.upload.and.returnValues(throwError(() => new HttpErrorResponse({ status: 400 })), throwError(() => new HttpErrorResponse({ status: 400 })),
      throwError(() => new HttpErrorResponse({ status: 409 })), throwError(() => new HttpErrorResponse({ status: 500 })),
      throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'This document holds malware (Eicar) and was not added' } })),
      throwError(() => new HttpErrorResponse({ status: 503 })),
      throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'This document needs a password to open, so it cannot be checked' } })),
      throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'This document is too large or holds too much to be checked' } })),
      throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'This document was refused by the safety check (PUA.Pdf.X)' } })));
    choose(element, [file('notes.exe')], false);
    choose(element, [file('x.webp')], true);
    choose(element, [file('c.pdf')], false);
    choose(element, [file('d.pdf')], false);
    fixture.componentInstance.clear();
    choose(element, [file('e.pdf')], false);
    choose(element, [file('f.pdf')], false);
    fixture.componentInstance.clear();
    choose(element, [file('g.pdf')], false);
    choose(element, [file('h.pdf')], false);
    choose(element, [file('i.pdf')], false);
    expect(notifications.error).toHaveBeenCalledWith('network.files.encrypted');
    expect(notifications.error).toHaveBeenCalledWith('network.files.unscannable');
    expect(notifications.error).toHaveBeenCalledWith('network.files.unsafe');
    expect(notifications.error).toHaveBeenCalledWith('network.files.malware');
    expect(notifications.error).toHaveBeenCalledWith('network.files.scanUnavailable');
    expect(notifications.error).toHaveBeenCalledWith('network.files.documentRefused');
    expect(notifications.error).toHaveBeenCalledWith('network.files.imageRefused');
    expect(notifications.error).toHaveBeenCalledWith('network.files.tooManyToday');
    expect(notifications.error).toHaveBeenCalledWith('network.files.uploadError');
    expect(fixture.componentInstance.valid).toBeFalse();
    const failed = fixture.componentInstance.picked[0];
    fixture.componentInstance.remove(failed);
    expect(api.discard).not.toHaveBeenCalled();
  });

  it('points out the workspace\'s names a document holds, until the member keeps it or removes it', () => {
    const element = create();
    api.upload.and.returnValue(of(uploaded(5, 'DOCUMENT', [{ found: 'Banque Martin', kind: 'CLIENT', city: 'Paris' },
      { found: 'Léa Roux', kind: 'CONTACT', city: null }])));
    choose(element, [file('mission.pdf')], false);
    expect(element.querySelector('[data-picked-confidential]')?.textContent).toContain('This document names Banque Martin, Léa Roux');
    expect(fixture.componentInstance.refs()).toEqual([{ id: 5 }]);
    (element.querySelector('[data-picked-keep]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-picked-confidential]')).toBeNull();
    expect(fixture.componentInstance.valid).toBeTrue();
  });
});
