import { TestBed } from '@angular/core/testing';
import { MatDialogModule } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../assets/i18n/en.json';
import { ShortcutHelpComponent } from './shortcut-help.component';

describe('ShortcutHelpComponent', () => {
  it('lists the global shortcuts and the offer feed keys', () => {
    TestBed.configureTestingModule({ imports: [ShortcutHelpComponent, MatDialogModule, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ShortcutHelpComponent);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).innerText.replace(/\s+/g, ' ');

    expect(text).toContain('Alt + Shift + N');
    expect(text).toContain('In the offer feed');
    expect(fixture.nativeElement.querySelectorAll('[data-feed-shortcuts] div').length).toBe(7);
    expect(text).toContain('Add the offer to the pipeline');
    expect(text).toContain('Write a note on the offer');
  });
});
