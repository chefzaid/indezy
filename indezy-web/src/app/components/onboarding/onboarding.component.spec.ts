import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { MatStepper } from '@angular/material/stepper';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NEVER, of, throwError } from 'rxjs';

import { ONBOARDING_SKILLS_MAX, OnboardingComponent } from './onboarding.component';
import { STARTER_PROCESSES, starterSteps } from './onboarding-processes';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import { PipelineTemplateService } from '../../services/pipeline-template/pipeline-template.service';
import { UserManagementService } from '../../services/user-management/user-management.service';
import { UserProfile } from '../../models/user-management.models';
import { PipelineTemplate } from '../../models';
import { User } from '../../models/auth.models';

describe('OnboardingComponent', () => {
  let fixture: ComponentFixture<OnboardingComponent>;
  let component: OnboardingComponent;
  let users: jasmine.SpyObj<UserManagementService>;
  let templates: jasmine.SpyObj<PipelineTemplateService>;
  let auth: jasmine.SpyObj<AuthService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let router: Router;
  let stepper: jasmine.SpyObj<MatStepper>;

  const profile: UserProfile = {
    id: 3, firstName: 'Alice', lastName: 'Martin', email: 'alice@example.test', skills: ['Java'], languages: ['French'],
    targetDailyRate: null, availabilityDate: null
  };

  const chip = (value: string): HTMLInputElement => Object.assign(document.createElement('input'), { value });

  beforeEach(async () => {
    users = jasmine.createSpyObj<UserManagementService>('UserManagementService', ['getUserProfile', 'updateUserProfile']);
    templates = jasmine.createSpyObj<PipelineTemplateService>('PipelineTemplateService', ['createFromSteps']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['getUser', 'completeOnboarding']);
    notifications = jasmine.createSpyObj<NotificationService>('NotificationService', ['error', 'success']);
    stepper = jasmine.createSpyObj<MatStepper>('MatStepper', ['next']);
    auth.getUser.and.returnValue({ id: 1, firstName: 'Ali', onboardingPending: true } as User);
    users.getUserProfile.and.returnValue(of(profile));
    users.updateUserProfile.and.callFake(update => of({ ...profile, ...update } as UserProfile));
    templates.createFromSteps.and.returnValue(of({ id: 1 } as PipelineTemplate));
    auth.completeOnboarding.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [OnboardingComponent, TranslateModule.forRoot()],
      providers: [
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        provideRouter([]),
        { provide: UserManagementService, useValue: users },
        { provide: PipelineTemplateService, useValue: templates },
        { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notifications }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(OnboardingComponent);
    component = fixture.componentInstance;
  });

  it('fills the steps in from the profile and greets its owner', () => {
    fixture.detectChanges();

    expect(component.isLoading).toBeFalse();
    expect(component.firstName).toBe('Alice');
    expect(component.aboutForm.getRawValue()).toEqual({ firstName: 'Alice', lastName: 'Martin', position: '', address: '', city: '' });
    expect(component.skills).toEqual(['Java']);
    expect((fixture.nativeElement as HTMLElement).querySelector('mat-stepper')).not.toBeNull();
  });

  it('reports a profile that cannot be loaded and saves nothing then', () => {
    users.getUserProfile.and.returnValue(throwError(() => new Error('down')));
    fixture.detectChanges();

    expect(component.isLoading).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('onboarding.loadError');
    component.saveProfile(stepper);
    expect(users.updateUserProfile).not.toHaveBeenCalled();
  });

  it('adds skills once whatever their spelling, up to the limit, and removes them', () => {
    fixture.detectChanges();
    const java = chip(' java ');
    const enter = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
    component.addSkill(java, enter);
    component.addSkill(chip('Kafka'));
    component.addSkill(chip('  '));
    expect(component.skills).toEqual(['Java', 'Kafka']);
    expect(java.value).toBe('');
    expect(enter.defaultPrevented).toBeTrue();

    component.skills = Array.from({ length: ONBOARDING_SKILLS_MAX }, (_, index) => `Skill ${index}`);
    component.addSkill(chip('One more'));
    expect(component.skills.length).toBe(ONBOARDING_SKILLS_MAX);

    component.removeSkill('Skill 0');
    expect(component.skills).not.toContain('Skill 0');
  });

  it('saves the profile with the first two steps, keeping what it already had', () => {
    fixture.detectChanges();
    component.aboutForm.patchValue({ firstName: ' Alicia ', position: 'Java lead', address: '1 rue de Rivoli', city: 'Paris' });
    component.addSkill(chip('Kafka'));
    component.rateForm.patchValue({ targetDailyRate: 650, availabilityDate: '2026-12-01' });

    component.saveProfile(stepper);

    expect(users.updateUserProfile).toHaveBeenCalledWith(jasmine.objectContaining({
      id: 3, email: 'alice@example.test', languages: ['French'], firstName: 'Alicia', lastName: 'Martin', position: 'Java lead',
      address: '1 rue de Rivoli', city: 'Paris', skills: ['Java', 'Kafka'], targetDailyRate: 650, availabilityDate: '2026-12-01'
    }));
    expect(component.firstName).toBe('Alicia');
    expect(stepper.next).toHaveBeenCalled();
    expect(component.isSaving).toBeFalse();
  });

  it('clears empty fields, refuses invalid ones and reports a failed save', () => {
    fixture.detectChanges();
    component.rateForm.patchValue({ targetDailyRate: 0 });
    component.saveProfile(stepper);
    expect(users.updateUserProfile).not.toHaveBeenCalled();
    expect(component.rateForm.touched).toBeTrue();

    component.rateForm.patchValue({ targetDailyRate: null, availabilityDate: '' });
    users.updateUserProfile.and.returnValue(throwError(() => new Error('down')));
    component.saveProfile(stepper);
    expect(users.updateUserProfile).toHaveBeenCalledWith(jasmine.objectContaining({
      position: undefined, address: undefined, city: undefined, targetDailyRate: null, availabilityDate: null
    }));
    expect(stepper.next).not.toHaveBeenCalled();
    expect(notifications.error).toHaveBeenCalledWith('onboarding.saveError');

    users.updateUserProfile.and.returnValue(NEVER);
    component.saveProfile(stepper);
    component.saveProfile(stepper);
    expect(users.updateUserProfile).toHaveBeenCalledTimes(2);
  });

  it('saves the chosen starter process in the interface language, once', () => {
    const translate = TestBed.inject(TranslateService);
    spyOn(translate, 'instant').and.callFake(((key: string) => `«${key}»`) as never);
    fixture.detectChanges();

    component.saveProcess(stepper);
    const esn = STARTER_PROCESSES[0];
    expect(templates.createFromSteps).toHaveBeenCalledWith('«onboarding.process.esn»', starterSteps(esn, key => `«${key}»`));
    expect(templates.createFromSteps.calls.mostRecent().args[1][0])
      .toEqual({ title: '«onboarding.process.steps.esnCall»', checklist: ['«onboarding.process.checklist.rate»',
        '«onboarding.process.checklist.availability»'] });
    expect(stepper.next).toHaveBeenCalledTimes(1);

    component.saveProcess(stepper);
    expect(templates.createFromSteps).toHaveBeenCalledTimes(1);
    expect(stepper.next).toHaveBeenCalledTimes(2);

    component.processForm.setValue({ process: 'DIRECT' });
    templates.createFromSteps.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.saveProcess(stepper);
    expect(stepper.next).toHaveBeenCalledTimes(3);
  });

  it('saves no process when none is wanted, and reports a failure', () => {
    fixture.detectChanges();
    component.processForm.setValue({ process: 'NONE' });
    component.saveProcess(stepper);
    expect(templates.createFromSteps).not.toHaveBeenCalled();
    expect(stepper.next).toHaveBeenCalled();

    component.processForm.setValue({ process: 'DIRECT' });
    templates.createFromSteps.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.saveProcess(stepper);
    expect(notifications.error).toHaveBeenCalledWith('onboarding.processError');
    expect(component.isSaving).toBeFalse();

    templates.createFromSteps.and.returnValue(NEVER);
    component.saveProcess(stepper);
    component.saveProcess(stepper);
    expect(templates.createFromSteps).toHaveBeenCalledTimes(2);
  });

  it('ends the welcome steps and opens the page chosen, from the last step or by skipping', () => {
    fixture.detectChanges();
    component.finish('/projects/new');
    expect(auth.completeOnboarding).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/projects/new');

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-skip]')!.click();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('stays when the welcome steps cannot be ended, and ends them once at a time', () => {
    fixture.detectChanges();
    auth.completeOnboarding.and.returnValue(throwError(() => new Error('down')));
    component.finish('/dashboard');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(notifications.error).toHaveBeenCalledWith('onboarding.finishError');

    auth.completeOnboarding.and.returnValue(NEVER);
    component.finish('/dashboard');
    component.finish('/offers');
    expect(auth.completeOnboarding).toHaveBeenCalledTimes(2);
  });
});
