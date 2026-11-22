import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { OfferSkillsGap } from '../../../models';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferSkillsGapComponent } from './offer-skills-gap.component';

describe('OfferSkillsGapComponent', () => {
  function create(answer: Observable<OfferSkillsGap>): HTMLElement {
    const offers = jasmine.createSpyObj('OfferService', ['skillsGap']);
    offers.skillsGap.and.returnValue(answer);
    TestBed.configureTestingModule({
      imports: [OfferSkillsGapComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: OfferService, useValue: offers }]
    });
    const fixture = TestBed.createComponent(OfferSkillsGapComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('lists the skills the matching offers ask for that the profile lacks', () => {
    const element = create(of({ matchingOffers: 12, gaps: [{ skill: 'Kafka', offers: 6, percent: 50 }, { skill: 'Docker', offers: 3, percent: 25 }] }));
    const items = element.querySelectorAll('[data-skills-gap-item]');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Kafka');
    expect((items[0].querySelector('.bar span') as HTMLElement).style.width).toBe('50%');
    expect(element.querySelector('[data-skills-gap-training]')).not.toBeNull();
  });

  it('says when the profile has no skills or nothing is missing, and hides when it cannot be read', () => {
    expect(create(of({ matchingOffers: 0, gaps: [] })).textContent).toContain('offers.skillsGap.noSkills');
    TestBed.resetTestingModule();
    expect(create(of({ matchingOffers: 40, gaps: [] })).textContent).toContain('offers.skillsGap.none');
    TestBed.resetTestingModule();
    expect(create(throwError(() => new Error('down'))).querySelector('[data-skills-gap]')).toBeNull();
  });
});
