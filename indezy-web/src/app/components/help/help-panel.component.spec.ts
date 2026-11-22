import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { HelpPanelComponent, helpArticleFor } from './help-panel.component';

describe('HelpPanelComponent', () => {
  it('finds the article of each page of the app, the getting started one elsewhere', () => {
    expect(helpArticleFor('/dashboard').slug).toBe('dashboard');
    expect(helpArticleFor('/projects/42?tab=journal').slug).toBe('pipeline');
    expect(helpArticleFor('/offers/7').slug).toBe('offers');
    expect(helpArticleFor('/contacts/2').slug).toBe('clients-contacts');
    expect(helpArticleFor('/invoices').slug).toBe('invoices-quotes');
    expect(helpArticleFor('/expenses').slug).toBe('invoices-quotes');
    expect(helpArticleFor('/network/messages').slug).toBe('network');
    expect(helpArticleFor('/profile#billing').slug).toBe('account');
    expect(helpArticleFor('/somewhere').slug).toBe('getting-started');
  });

  it('shows the article and closes as it opens the full one', () => {
    const close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [HelpPanelComponent, TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: MAT_DIALOG_DATA, useValue: helpArticleFor('/offers') }, { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(HelpPanelComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne(request => request.url.endsWith('/offers.md')).flush('# The offer feed');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-help-panel]').textContent).toContain('The offer feed');
    fixture.componentInstance.close();
    expect(close).toHaveBeenCalled();
  });
});
