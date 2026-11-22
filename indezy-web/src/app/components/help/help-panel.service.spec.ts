import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { HelpPanelComponent } from './help-panel.component';
import { HelpPanelService } from './help-panel.service';

describe('HelpPanelService', () => {
  it('opens the panel at the side on the page\'s article or on a section', async () => {
    const open = jasmine.createSpy('open');
    TestBed.configureTestingModule({ providers: [{ provide: MatDialog, useValue: { open } }] });
    const service = TestBed.inject(HelpPanelService);

    await service.openForPage('/invoices/3');
    expect(open).toHaveBeenCalledWith(HelpPanelComponent, jasmine.objectContaining({ data: { slug: 'invoices-quotes' },
      position: { right: '0', top: '0' }, panelClass: 'help-panel-dialog' }));
    await service.open({ slug: 'offers', section: { en: 'filter-and-sort', fr: 'filtrer-et-trier' } });
    expect(open.calls.mostRecent().args[1].data).toEqual({ slug: 'offers', section: { en: 'filter-and-sort', fr: 'filtrer-et-trier' } });
  });
});
