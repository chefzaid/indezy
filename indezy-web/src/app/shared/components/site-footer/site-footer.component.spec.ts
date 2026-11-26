import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import en from '../../../../assets/i18n/en.json';
import { AuthService } from '../../../services/auth/auth.service';
import { SiteFooterComponent } from './site-footer.component';

describe('SiteFooterComponent', () => {
  function create(signedIn: boolean, contained = false): HTMLElement {
    TestBed.configureTestingModule({
      imports: [SiteFooterComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: () => signedIn } }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(SiteFooterComponent);
    fixture.componentInstance.contained = contained;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the copyright, tagline and public links for a visitor', () => {
    const element = create(false, true);
    const footer = element.querySelector('[data-site-footer]')!;
    expect(footer.classList.contains('contained')).toBeTrue();
    expect(footer.textContent).toContain(`© ${new Date().getFullYear()} Indezy`);
    expect(footer.textContent).toContain('The workspace of IT freelancers in France.');
    expect(element.querySelector('[data-footer-link="help"]')?.getAttribute('href')).toBe('/help');
    expect(element.querySelector('[data-footer-link="support"]')).toBeNull();
    expect(element.querySelector('[data-footer-link="notice"]')?.getAttribute('href')).toBe('/legal/notice');
    expect(element.querySelector('[data-footer-link="terms"]')?.getAttribute('href')).toBe('/legal/terms');
    expect(element.querySelector('[data-footer-link="privacy"]')?.getAttribute('href')).toBe('/legal/privacy');
    expect(element.querySelector('[data-footer-link="contact"]')?.getAttribute('href')).toBe('mailto:contact@swirlit.dev');
  });

  it('includes the Support link for a signed-in member and spans the page width by default', () => {
    const element = create(true);
    const footer = element.querySelector('[data-site-footer]')!;
    expect(footer.classList.contains('contained')).toBeFalse();
    expect(element.querySelector('[data-footer-link="support"]')?.getAttribute('href')).toBe('/support');
  });
});
