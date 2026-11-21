import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import en from '../../../../assets/i18n/en.json';
import { RateBenchmarkService, RateFigure, RateSkills } from '../../../services/rate-benchmark/rate-benchmark.service';
import { RateBenchmarkCardComponent } from './rate-benchmark-card.component';

describe('RateBenchmarkCardComponent', () => {
  let benchmark: jasmine.SpyObj<RateBenchmarkService>;
  const figure = (changes: Partial<RateFigure> = {}): RateFigure => ({ skill: 'Java', seniority: null, region: null, freelancers: 7, threshold: 5,
    median: 650, low: 600, high: 720, sharing: false, ...changes });

  function create(skills: RateSkills | null): ComponentFixture<RateBenchmarkCardComponent> {
    benchmark = jasmine.createSpyObj('RateBenchmarkService', ['skills', 'figure']);
    benchmark.skills.and.returnValue(skills ? of(skills) : throwError(() => new Error('not a member')));
    benchmark.figure.and.returnValue(of(figure()));
    TestBed.configureTestingModule({
      imports: [RateBenchmarkCardComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: RateBenchmarkService, useValue: benchmark }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(RateBenchmarkCardComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the median of the most backed skill, and asks to share when the reader does not', () => {
    const fixture = create({ skills: [{ skill: 'Java', freelancers: 7 }, { skill: 'React', freelancers: 5 }], threshold: 5, sharing: false });
    const element: HTMLElement = fixture.nativeElement;
    expect(benchmark.figure).toHaveBeenCalledWith('Java', null, null);
    expect(element.querySelector('[data-benchmark-median]')?.textContent).toContain('650 € a day, median');
    expect(element.querySelector('[data-benchmark-range]')?.textContent).toContain('600 € and 720 €, from 7 freelancers');
    expect(element.querySelector('[data-benchmark-share]')?.getAttribute('href')).toBe('/profile?tab=privacy');
  });

  it('narrows to a seniority and a region, and hides a figure too few back', () => {
    const fixture = create({ skills: [{ skill: 'Java', freelancers: 7 }], threshold: 5, sharing: true });
    benchmark.figure.and.returnValue(of(figure({ freelancers: 2, median: null, low: null, high: null })));
    fixture.componentInstance.choose({ seniority: 'SENIOR' });
    fixture.componentInstance.choose({ region: 'REMOTE' });
    fixture.detectChanges();
    expect(benchmark.figure).toHaveBeenCalledWith('Java', 'SENIOR', 'REMOTE');
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-benchmark-too-few]')?.textContent).toContain('2 freelancer(s) for these choices');
    expect(element.querySelector('[data-benchmark-share]')).toBeNull();
    fixture.componentInstance.choose({ skill: 'React' });
    expect(benchmark.figure).toHaveBeenCalledWith('React', 'SENIOR', 'REMOTE');
  });

  it('says when no skill has enough shared rates, and shows nothing outside the network', () => {
    const empty = create({ skills: [], threshold: 5, sharing: false });
    expect(empty.nativeElement.querySelector('[data-benchmark-empty]')).not.toBeNull();
    expect(benchmark.figure).not.toHaveBeenCalled();
    TestBed.resetTestingModule();
    const outside = create(null);
    expect(outside.nativeElement.querySelector('[data-rate-benchmark]')).toBeNull();
  });
});
