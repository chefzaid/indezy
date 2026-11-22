import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HelpLinkComponent } from './help-link.component';
import { HelpPanelService } from './help-panel.service';

describe('HelpLinkComponent', () => {
  it('opens the help panel at the section explaining the setting, and only that', () => {
    const open = jasmine.createSpy('open').and.resolveTo();
    TestBed.configureTestingModule({
      imports: [HelpLinkComponent, TranslateModule.forRoot()],
      providers: [{ provide: HelpPanelService, useValue: { open } }]
    });
    const fixture = TestBed.createComponent(HelpLinkComponent);
    fixture.componentRef.setInput('topic', 'facturX');
    fixture.detectChanges();
    const submitted = jasmine.createSpy('submit');
    const form = document.createElement('form');
    form.addEventListener('submit', submitted);
    form.appendChild(fixture.nativeElement);
    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-help-link="facturX"]')!;
    expect(button.getAttribute('aria-label')).toBe('help.link.facturX');
    button.click();
    expect(open).toHaveBeenCalledWith({ slug: 'invoices-quotes', section: { en: 'factur-x', fr: 'factur-x' } });
    expect(submitted).not.toHaveBeenCalled();
  });
});
