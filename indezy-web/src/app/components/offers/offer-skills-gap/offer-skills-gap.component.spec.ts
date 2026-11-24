import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { CatalogTraining, OfferSkillsGap, Training } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { TrainingService } from '../../../services/training/training.service';
import { OfferSkillsGapComponent } from './offer-skills-gap.component';

describe('OfferSkillsGapComponent', () => {
  let offers: jasmine.SpyObj<OfferService>;
  let trainings: jasmine.SpyObj<TrainingService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const kafka: CatalogTraining = { id: 'k1', title: 'Apache Kafka', provider: 'AMBIENT IT', certification: 'TP CDA', hours: 21, price: 2400,
    remote: true, regions: ['Île-de-France'] };

  function create(answer: Observable<OfferSkillsGap>): HTMLElement {
    offers = jasmine.createSpyObj('OfferService', ['skillsGap', 'skillTrainings']);
    offers.skillsGap.and.returnValue(answer);
    trainings = jasmine.createSpyObj('TrainingService', ['create']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [OfferSkillsGapComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: OfferService, useValue: offers }, { provide: TrainingService, useValue: trainings },
        { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(OfferSkillsGapComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  const gaps = (): Observable<OfferSkillsGap> => of({ matchingOffers: 12, gaps: [{ skill: 'Kafka', offers: 6, percent: 50 },
    { skill: 'Docker', offers: 3, percent: 25 }] });

  it('lists the skills the matching offers ask for that the profile lacks', () => {
    const element = create(gaps());
    const items = element.querySelectorAll('[data-skills-gap-item]');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Kafka');
    expect((items[0].querySelector('.bar span') as HTMLElement).style.width).toBe('50%');
    expect(element.querySelector('[data-skills-gap-training]')).not.toBeNull();
  });

  it('shows the CPF trainings of a skill on request, read once, and says when the catalogue fails', () => {
    const element = create(gaps());
    const fixture = TestBed.inject(MatDialog) && element;
    offers.skillTrainings.and.returnValue(throwError(() => new Error('down')));
    (element.querySelector('[data-skills-gap-cpf="Kafka"]') as HTMLButtonElement).click();
    TestBed.tick();
    expect(fixture.querySelector('[data-skills-gap-cpf-error]')).not.toBeNull();

    offers.skillTrainings.and.returnValue(of([kafka]));
    const button = element.querySelector('[data-skills-gap-cpf="Kafka"]') as HTMLButtonElement;
    button.click();
    TestBed.tick();
    button.click();
    TestBed.tick();
    expect(offers.skillTrainings).toHaveBeenCalledWith('Kafka');
    const shown = element.querySelector('[data-catalog-training="k1"]')!;
    expect(shown.textContent).toContain('Apache Kafka');
    expect(shown.textContent).toContain('offers.skillsGap.remote');
    // Hidden, then shown again without reading it again.
    button.click();
    TestBed.tick();
    expect(element.querySelector('[data-skills-gap-trainings]')).toBeNull();
    button.click();
    TestBed.tick();
    expect(offers.skillTrainings).toHaveBeenCalledTimes(2);

    offers.skillTrainings.and.returnValue(of([]));
    (element.querySelector('[data-skills-gap-cpf="Docker"]') as HTMLButtonElement).click();
    TestBed.tick();
    expect(element.querySelector('[data-skills-gap-cpf-none]')).not.toBeNull();
  });

  it('plans a catalogue training through the training form', () => {
    const element = create(gaps());
    offers.skillTrainings.and.returnValue(of([kafka]));
    (element.querySelector('[data-skills-gap-cpf="Kafka"]') as HTMLButtonElement).click();
    TestBed.tick();
    const open = spyOn(TestBed.inject(MatDialog), 'open').and.returnValue({ afterClosed: () => of({ title: 'Apache Kafka' }) } as never);
    trainings.create.and.returnValue(of({ id: 4, title: 'Apache Kafka' } as Training));
    (element.querySelector('[data-catalog-plan="k1"]') as HTMLButtonElement).click();
    expect(open.calls.mostRecent().args[1]!.data).toEqual(jasmine.objectContaining({ title: 'Apache Kafka', provider: 'AMBIENT IT',
      hours: 21, cost: 2400, notes: 'CPF' }));
    expect(trainings.create).toHaveBeenCalledWith({ title: 'Apache Kafka' });
    expect(notifications.success).toHaveBeenCalledWith('offers.skillsGap.planned');
  });

  it('says when the profile has no skills or nothing is missing, and hides when it cannot be read', () => {
    expect(create(of({ matchingOffers: 0, gaps: [] })).textContent).toContain('offers.skillsGap.noSkills');
    TestBed.resetTestingModule();
    expect(create(of({ matchingOffers: 40, gaps: [] })).textContent).toContain('offers.skillsGap.none');
    TestBed.resetTestingModule();
    expect(create(throwError(() => new Error('down'))).querySelector('[data-skills-gap]')).toBeNull();
  });
});
