import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import fr from '../../../../assets/i18n/fr.json';
import { OfferView } from '../../../models/offer.models';
import { OfferBulkAction, OfferSelectionBarComponent } from './offer-selection-bar.component';

describe('OfferSelectionBarComponent', () => {
  function create(count: number, view: OfferView = 'FEED'): ComponentFixture<OfferSelectionBarComponent> {
    TestBed.configureTestingModule({ imports: [OfferSelectionBarComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferSelectionBarComponent);
    fixture.componentRef.setInput('count', count);
    fixture.componentRef.setInput('pageCount', 3);
    fixture.componentRef.setInput('view', view);
    fixture.detectChanges();
    return fixture;
  }

  function click(fixture: ComponentFixture<OfferSelectionBarComponent>, selector: string): void {
    ((fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement).click();
  }

  function actions(fixture: ComponentFixture<OfferSelectionBarComponent>): OfferBulkAction[] {
    const emitted: OfferBulkAction[] = [];
    fixture.componentInstance.act.subscribe(action => emitted.push(action));
    for (const selector of ['[data-bulk-save]', '[data-bulk-hide]', '[data-bulk-seen]', '[data-bulk-add]']) {
      click(fixture, selector);
    }
    return emitted;
  }

  it('counts the selected offers and acts on all of them', () => {
    const fixture = create(1);

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-selected-count]')?.textContent).toContain('1 sélectionnée');
    expect(actions(fixture)).toEqual([{ update: { saved: true } }, { update: { hidden: true } }, { update: { seen: true } }, 'add']);
  });

  it('unsaves in the saved view and shows again in the hidden one', () => {
    expect(actions(create(2, 'SAVED'))[0]).toEqual({ update: { saved: false } });
    TestBed.resetTestingModule();
    const hidden = create(2, 'HIDDEN');
    expect((hidden.nativeElement as HTMLElement).innerText).toContain('Réafficher');
    expect(actions(hidden)[1]).toEqual({ update: { hidden: false } });
  });

  it('selects the whole page, then none, and clears the selection', () => {
    const fixture = create(2);
    const events: string[] = [];
    fixture.componentInstance.selectPage.subscribe(() => events.push('page'));
    fixture.componentInstance.clear.subscribe(() => events.push('clear'));

    fixture.componentInstance.toggleAll();
    fixture.componentRef.setInput('count', 3);
    fixture.detectChanges();
    fixture.componentInstance.toggleAll();
    click(fixture, '[data-bulk-clear]');

    expect(events).toEqual(['page', 'clear', 'clear']);
    expect((fixture.nativeElement as HTMLElement).innerText).toContain('3 sélectionnées');
  });
});
