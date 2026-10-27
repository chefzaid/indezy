import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { PublicPageHeaderComponent } from './public-page-header.component';
import { LANGUAGE_STORAGE_KEY } from '../../locale/app-locale';

describe('PublicPageHeaderComponent', () => {
  it('switches the language with a reload that keeps the address', () => {
    TestBed.configureTestingModule({ imports: [PublicPageHeaderComponent, TranslateModule.forRoot()] });
    const fixture = TestBed.createComponent(PublicPageHeaderComponent);
    fixture.detectChanges();
    const header = fixture.componentInstance;
    const reload = spyOn(header, 'reloadPage');
    const store = spyOn(localStorage, 'setItem');

    header.switchLanguage(header.language);
    expect(reload).not.toHaveBeenCalled();

    const other = header.language === 'fr' ? 'en' : 'fr';
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(`button[lang="${other}"]`)!.click();
    expect(store).toHaveBeenCalledWith(LANGUAGE_STORAGE_KEY, other);
    expect(reload).toHaveBeenCalled();
  });
});
