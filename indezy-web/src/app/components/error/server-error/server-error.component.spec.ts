import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../../assets/i18n/en.json';
import { ErrorDisplayComponent } from '../../../shared/components/error-display/error-display.component';
import { ServerErrorComponent } from './server-error.component';

describe('ServerErrorComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ServerErrorComponent, TranslateModule.forRoot()],
      providers: [provideRouter([])]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
  });

  it('explains the server error and offers to retry or go home', () => {
    const fixture = TestBed.createComponent(ServerErrorComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.error-title')!.textContent!.trim()).toBe(en.errors.serverErrorTitle);
    expect(element.querySelector('.error-message')!.textContent!.trim()).toBe(en.errors.serverErrorMessage);
    expect(element.querySelector('.error-icon mat-icon')!.textContent).toBe('error_outline');
    expect(element.textContent).toContain(en.common.retry);
    expect(element.querySelector('button[routerLink="/dashboard"]')).not.toBeNull();
  });

  it('reloads the page when the user retries', () => {
    const fixture = TestBed.createComponent(ServerErrorComponent);
    fixture.detectChanges();
    // A real reload would restart the test runner, so stand in for the component's handler.
    const onRetry = spyOn(fixture.componentInstance, 'onRetry');

    fixture.debugElement.query(By.directive(ErrorDisplayComponent)).componentInstance.retry.emit();

    expect(onRetry).toHaveBeenCalled();
  });
});
