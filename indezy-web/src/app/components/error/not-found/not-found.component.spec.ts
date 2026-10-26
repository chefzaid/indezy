import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../../assets/i18n/en.json';
import { NotFoundComponent } from './not-found.component';

describe('NotFoundComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NotFoundComponent, TranslateModule.forRoot()],
      providers: [provideRouter([])]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
  });

  it('explains that the page does not exist and only offers to go home', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(fixture.componentInstance.errorConfig).toEqual(jasmine.objectContaining({ type: 'not-found', showRetry: false }));
    expect(element.querySelector('.error-title')!.textContent!.trim()).toBe(en.errors.notFoundTitle);
    expect(element.querySelector('.error-message')!.textContent!.trim()).toBe(en.errors.notFoundMessage);
    expect(element.querySelector('.error-icon mat-icon')!.textContent).toBe('search_off');
    const buttons = element.querySelectorAll('button');
    expect(buttons.length).toBe(1);
    expect(buttons[0].getAttribute('routerLink')).toBe('/dashboard');
  });
});
