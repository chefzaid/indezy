import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatChipInputEvent } from '@angular/material/chips';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { OfferFilters, OfferRegion, emptyOfferFilters } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferFiltersComponent } from './offer-filters.component';

describe('OfferFiltersComponent', () => {
  const regions: OfferRegion[] = [
    { regionCode: '11', name: 'Île-de-France', departments: [{ departmentCode: '75', name: 'Paris' }, { departmentCode: '92', name: 'Hauts-de-Seine' }] }
  ];
  let service: jasmine.SpyObj<OfferService>;
  let emitted: OfferFilters[];

  function create(filters: OfferFilters = emptyOfferFilters(), distanceAvailable = true): ComponentFixture<OfferFiltersComponent> {
    service = jasmine.createSpyObj('OfferService', ['skills']);
    service.skills.and.returnValue(of([{ value: 'Java', offers: 42 }, { value: 'Kafka', offers: 3 }]));
    TestBed.configureTestingModule({
      imports: [OfferFiltersComponent, TranslateModule.forRoot()],
      providers: [provideNativeDateAdapter(), { provide: LOCALE_ID, useValue: 'fr-FR' }, { provide: OfferService, useValue: service }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferFiltersComponent);
    fixture.componentRef.setInput('filters', filters);
    fixture.componentRef.setInput('regions', regions);
    fixture.componentRef.setInput('distanceAvailable', distanceAvailable);
    emitted = [];
    fixture.componentInstance.filtersChange.subscribe(value => emitted.push(value));
    fixture.detectChanges();
    return fixture;
  }

  function chipEvent(value: string): MatChipInputEvent {
    return { value, chipInput: { clear: jasmine.createSpy('clear') } } as unknown as MatChipInputEvent;
  }

  it('waits for the user to stop typing before applying typed fields', fakeAsync(() => {
    const component = create().componentInstance;

    component.draft.q = '  java ';
    component.typed();
    component.draft.rateMin = 450.4;
    component.draft.rateMax = null as unknown as number;
    component.typed();
    tick(399);
    expect(emitted).toEqual([]);
    tick(1);

    expect(emitted.length).toBe(1);
    expect(emitted[0].q).toBe('java');
    expect(emitted[0].rateMin).toBe(450);
    expect(emitted[0].rateMax).toBeUndefined();
  }));

  it('applies lists, toggles and dates at once', () => {
    const component = create().componentInstance;

    component.toggleWorkMode('REMOTE');
    component.toggleWorkMode('HYBRID');
    component.toggleWorkMode('REMOTE');
    component.dateChanged(new Date(2026, 11, 1));
    component.draft.unseenOnly = true;
    component.draft.radiusKm = 25;
    component.changed();

    expect(emitted.map(filters => filters.workModes)).toEqual([['REMOTE'], ['REMOTE', 'HYBRID'], ['HYBRID'], ['HYBRID'], ['HYBRID']]);
    expect(emitted[3].startBefore).toBe('2026-12-01');
    expect(emitted[4].unseenOnly).toBeTrue();
    expect(emitted[4].radiusKm).toBe(25);
  });

  it('drops the distance while the profile has no city and explains why', () => {
    const fixture = create(emptyOfferFilters(), false);
    const component = fixture.componentInstance;

    component.draft.radiusKm = 25;
    component.changed();

    expect(emitted[0].radiusKm).toBeUndefined();
    expect((fixture.nativeElement as HTMLElement).innerText).toContain('Renseignez votre ville dans le profil');
  });

  it('keeps the offers matching the profile only once it names skills', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const toggle = (): HTMLButtonElement => fixture.nativeElement.querySelector('.match-toggle button[role="switch"]');

    expect(toggle().disabled).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).innerText).toContain("Ajoutez d'abord des compétences à votre profil");
    component.draft.matchesProfile = true;
    component.changed();
    fixture.componentRef.setInput('matchAvailable', true);
    fixture.detectChanges();
    component.draft.matchesProfile = true;
    component.changed();

    expect(emitted.map(filters => filters.matchesProfile)).toEqual([undefined, true]);
    expect(toggle().disabled).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).innerText).not.toContain("Ajoutez d'abord des compétences");
  });

  it('adds skills once, from typing or suggestions, and removes them', () => {
    const component = create().componentInstance;

    component.addSkill(chipEvent(' Java '));
    component.addSkill(chipEvent('java'));
    component.pickSkill({ option: { value: 'Kafka' } } as MatAutocompleteSelectedEvent);
    component.addSkill(chipEvent('  '));
    component.removeSkill('Java');

    expect(emitted.map(filters => filters.skills)).toEqual([['Java'], ['Java', 'Kafka'], ['Kafka']]);
    component.draft.skills = Array.from({ length: 10 }, (_, i) => `s${i}`);
    component.addSkill(chipEvent('Rust'));
    expect(component.draft.skills).not.toContain('Rust');
  });

  it('suggests skills of the open offers that are not chosen yet', fakeAsync(() => {
    const component = create({ ...emptyOfferFilters(), skills: ['Java'] }).componentInstance;

    component.skillTyped('ja');
    tick(250);

    expect(service.skills).toHaveBeenCalledWith('ja');
    expect(component.skillSuggestions).toEqual([{ value: 'Kafka', offers: 3 }]);

    component.skillTyped(' ');
    tick(250);
    expect(component.skillSuggestions).toEqual([]);
  }));

  it('forgets suggestions when they cannot be loaded', fakeAsync(() => {
    const component = create().componentInstance;
    service.skills.and.returnValue(throwError(() => new Error('down')));

    component.skillTyped('ja');
    tick(250);

    expect(component.skillSuggestions).toEqual([]);
  }));

  it('starts from the given filters and clears them all', () => {
    const filters: OfferFilters = { ...emptyOfferFilters(), q: 'java', departments: ['92', '13'], startBefore: '2026-12-01', sites: ['YEETS'] };
    const fixture = create(filters);
    const component = fixture.componentInstance;

    expect(component.draft).toEqual(filters);
    expect(component.draft.departments).not.toBe(filters.departments);
    expect(component.startBefore?.getDate()).toBe(1);
    expect(component.departmentsSummary).toBe('Hauts-de-Seine, 13');
    expect(component.activeCount).toBe(4);
    expect((fixture.nativeElement as HTMLElement).innerText).toContain('Effacer les filtres (4)');

    component.clear();

    expect(emitted[0]).toEqual(jasmine.objectContaining({ ...emptyOfferFilters(), q: undefined }));
    expect(component.startBefore).toBeNull();
  });
});
