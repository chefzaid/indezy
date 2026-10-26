import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../../assets/i18n/en.json';
import { ErrorDisplayComponent, ErrorDisplayConfig } from './error-display.component';

describe('ErrorDisplayComponent', () => {
  let fixture: ComponentFixture<ErrorDisplayComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ErrorDisplayComponent, TranslateModule.forRoot()],
      providers: [provideRouter([])]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    fixture = TestBed.createComponent(ErrorDisplayComponent);
  });

  function render(config?: ErrorDisplayConfig): HTMLElement {
    if (config) {
      fixture.componentRef.setInput('config', config);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const textOf = (element: HTMLElement, selector: string): string => element.querySelector(selector)!.textContent!.trim();

  it('shows a generic error with the logo, retry and home actions by default', () => {
    const element = render();

    expect(element.querySelector('app-logo')).not.toBeNull();
    expect(textOf(element, '.error-icon mat-icon')).toBe('error_outline');
    expect(element.querySelector('.error-icon')!.classList).toContain('error-error');
    expect(textOf(element, '.error-title')).toBe(en.errors.defaultErrorTitle);
    expect(textOf(element, '.error-message')).toBe(en.errors.defaultErrorMessage);
    expect(element.textContent).toContain(en.common.retry);
    expect(element.querySelector('button[routerLink="/dashboard"]')).not.toBeNull();
  });

  it('emits retry when the retry button is clicked', () => {
    const element = render();
    let retried = 0;
    fixture.componentInstance.retry.subscribe(() => retried++);

    (element.querySelector('button[color="primary"]') as HTMLButtonElement).click();

    expect(retried).toBe(1);
  });

  it('uses the icon, title and message of each kind of problem', () => {
    const cases: [ErrorDisplayConfig['type'], string, string, string][] = [
      ['warning', 'warning', en.errors.defaultWarningTitle, en.errors.defaultWarningMessage],
      ['info', 'info', en.errors.defaultInfoTitle, en.errors.defaultInfoMessage],
      ['not-found', 'search_off', en.errors.defaultNotFoundTitle, en.errors.defaultNotFoundMessage]
    ];

    for (const [type, icon, title, message] of cases) {
      const element = render({ type });
      expect(textOf(element, '.error-icon mat-icon')).withContext(String(type)).toBe(icon);
      expect(textOf(element, '.error-title')).withContext(String(type)).toBe(title);
      expect(textOf(element, '.error-message')).withContext(String(type)).toBe(message);
    }
  });

  it('prefers the given title and message and hides the disabled parts', () => {
    const element = render({ type: 'error', title: 'Boom', message: 'Try later', showLogo: false, showRetry: false, showHome: false });

    expect(textOf(element, '.error-title')).toBe('Boom');
    expect(textOf(element, '.error-message')).toBe('Try later');
    expect(element.querySelector('app-logo')).toBeNull();
    expect(element.querySelectorAll('button').length).toBe(0);
  });
});
