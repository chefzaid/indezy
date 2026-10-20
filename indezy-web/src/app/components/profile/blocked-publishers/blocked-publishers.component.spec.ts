import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { OfferPublisherBlock, publisherKey } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { BlockedPublishersComponent } from './blocked-publishers.component';

describe('BlockedPublishersComponent', () => {
  let service: jasmine.SpyObj<OfferService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(result: Observable<OfferPublisherBlock[]> = of([{ id: 8, name: 'Exemple Conseil' }])):
    ComponentFixture<BlockedPublishersComponent> {
    service = jasmine.createSpyObj('OfferService', ['blockedPublishers', 'blockPublisher', 'unblockPublisher']);
    service.blockedPublishers.and.returnValue(result);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [BlockedPublishersComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: OfferService, useValue: service }, { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(BlockedPublishersComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<BlockedPublishersComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).innerText.replace(/\s+/g, ' ');
  }

  it('lists the hidden publishers and links to the job boards', () => {
    const fixture = create();

    expect(text(fixture)).toContain('Éditeurs masqués');
    expect(text(fixture)).toContain('Exemple Conseil');
    expect(fixture.nativeElement.querySelector('[data-block="Exemple Conseil"] button').getAttribute('aria-label'))
      .toBe('Afficher à nouveau les offres de Exemple Conseil');
    expect(fixture.nativeElement.querySelector('a[href="/offers/sites"]')).not.toBeNull();
  });

  it('hides a publisher by its name, once', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    service.blockPublisher.and.returnValues(of({ id: 9, name: 'Acme' }), of({ id: 8, name: 'Exemple Conseil' }));

    component.name = ' — ';
    expect(component.canAdd).toBeFalse();
    component.add();
    component.name = '  Acme ';
    component.add();
    component.name = 'EXEMPLE-conseil';
    component.add();

    expect(service.blockPublisher.calls.allArgs()).toEqual([['Acme'], ['EXEMPLE-conseil']]);
    expect(component.blocks.map(block => block.name)).toEqual(['Acme', 'Exemple Conseil']);
    expect(component.name).toBe('');
    expect(notifications.success).toHaveBeenCalledWith('offers.notifications.publisherBlocked', 4000, { publisher: 'Acme' });
    service.blockPublisher.and.returnValue(throwError(() => new Error('down')));
    component.name = 'Beta';
    component.add();
    expect(component.saving).toBeFalse();
    expect(component.name).toBe('Beta');
  });

  it('shows a publisher again and puts it back when that fails', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    service.unblockPublisher.and.returnValue(of(undefined));

    (fixture.nativeElement.querySelector('[data-block="Exemple Conseil"] button') as HTMLButtonElement).click();

    expect(service.unblockPublisher).toHaveBeenCalledWith(8);
    expect(text(fixture)).toContain('Aucun éditeur masqué.');
    expect(notifications.success).toHaveBeenCalledWith('profile.blockedPublishers.unblocked', 4000, { publisher: 'Exemple Conseil' });
    component.blocks = [{ id: 9, name: 'Acme' }];
    service.unblockPublisher.and.returnValue(throwError(() => new Error('down')));
    component.remove(component.blocks[0]);
    expect(component.blocks.map(block => block.name)).toEqual(['Acme']);
  });

  it('says when the list cannot be loaded, and folds names as the server does', () => {
    const fixture = create(throwError(() => new Error('down')));

    expect(text(fixture)).toContain("Les éditeurs masqués n'ont pas pu être chargés.");
    expect(publisherKey('  Données & Cie ')).toBe('donnees cie');
    expect(publisherKey('C++ / .NET')).toBe('c++ .net');
    expect(publisherKey(undefined)).toBe('');
  });
});
