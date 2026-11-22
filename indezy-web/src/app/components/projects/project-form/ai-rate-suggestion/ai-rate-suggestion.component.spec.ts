import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AiService } from '../../../../services/ai/ai.service';
import { NotificationService } from '../../../../services/notification/notification.service';
import { AiRateSuggestionComponent } from './ai-rate-suggestion.component';

describe('AiRateSuggestionComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create() {
    ai = jasmine.createSpyObj('AiService', ['suggestRate']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [AiRateSuggestionComponent, TranslateModule.forRoot()],
      providers: [{ provide: AiService, useValue: ai }, { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(AiRateSuggestionComponent);
    fixture.componentInstance.form = new FormGroup({
      role: new FormControl(' Lead Kafka '), techStack: new FormControl('Kafka, Java'), workMode: new FormControl('HYBRID'),
      remoteDaysPerMonth: new FormControl('8'), workAddress: new FormControl(''), durationInMonths: new FormControl(''),
      description: new FormControl('Flux')
    });
    fixture.detectChanges();
    return fixture;
  }

  it('asks for a rate from what the form says, shows it and hands it on', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    ai.suggestRate.and.returnValue(of({ rate: 700, low: 650, high: 750, reasons: ['Kafka est rare'], marketMedian: 640 }));
    const used: number[] = [];
    component.use.subscribe(rate => used.push(rate));

    component.suggest();
    expect(ai.suggestRate).toHaveBeenCalledOnceWith({ role: 'Lead Kafka', techStack: 'Kafka, Java', workMode: 'HYBRID' as never,
      remoteDaysPerMonth: 8, place: null, durationInMonths: null, description: 'Flux', language: 'FR' });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-ai-rate-suggestion]')?.textContent).toContain('Kafka est rare');
    expect(element.querySelector('.market')).not.toBeNull();
    (element.querySelector('[data-ai-rate-use]') as HTMLButtonElement).click();
    expect(used).toEqual([700]);
  });

  it('says why no rate could be suggested', () => {
    const fixture = create();
    ai.suggestRate.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'AI is not available yet' } })));
    fixture.componentInstance.suggest();
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.unavailable');
    expect(fixture.componentInstance.busy).toBeFalse();
    expect(fixture.componentInstance.suggestion).toBeNull();
  });
});
