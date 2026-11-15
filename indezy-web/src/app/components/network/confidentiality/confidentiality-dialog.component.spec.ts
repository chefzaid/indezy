import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ConfidentialityData, ConfidentialityDialogComponent } from './confidentiality-dialog.component';
import en from '../../../../assets/i18n/en.json';

describe('ConfidentialityDialogComponent', () => {
  const data: ConfidentialityData = { text: 'Chez Société Générale via Alten, avec Hélène Durand et Hélène Durand.', matches: [
    { found: 'Société Générale', kind: 'CLIENT', city: 'Paris' }, { found: 'Alten', kind: 'ESN', city: null },
    { found: 'Hélène Durand', kind: 'CONTACT', city: null }] };
  let close: jasmine.Spy;

  function create(shown = data) {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [ConfidentialityDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: MAT_DIALOG_DATA, useValue: shown },
        { provide: MatDialogRef, useValue: { close } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ConfidentialityDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('names what it found and what each becomes, then anonymizes every occurrence or publishes anyway', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-confidential="Société Générale"]')?.textContent).toContain('a client in Paris');
    expect(element.querySelector('[data-confidential="Alten"]')?.textContent).toContain('a consultancy');
    (element.querySelector('[data-confidential-anonymize]') as HTMLButtonElement).click();
    expect(close).toHaveBeenCalledWith({ action: 'edit', text: 'Chez a client in Paris via a consultancy, avec a contact et a contact.' });
    (element.querySelector('[data-confidential-publish]') as HTMLButtonElement).click();
    expect(close).toHaveBeenCalledWith({ action: 'publish', text: data.text });
  });

  it('calls a client without a city a client', () => {
    const fixture = create({ text: 'Chez Acme', matches: [{ found: 'Acme', kind: 'CLIENT', city: null }] });
    expect(fixture.componentInstance.anonymized()).toBe('Chez a client');
  });
});
