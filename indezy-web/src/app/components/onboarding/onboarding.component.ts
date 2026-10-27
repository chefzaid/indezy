import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, map, takeUntil } from 'rxjs';

import { UserProfile } from '../../models/user-management.models';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import { PipelineTemplateService } from '../../services/pipeline-template/pipeline-template.service';
import { UserManagementService } from '../../services/user-management/user-management.service';
import { skillKey } from '../../shared/utils/skill-names';
import { STARTER_PROCESSES, StarterProcess, starterSteps } from './onboarding-processes';

/** Most skills the welcome step takes, as the profile's skills editor allows more later. */
export const ONBOARDING_SKILLS_MAX = 30;

/**
 * Welcome steps of a new account: who they are and where they work from, the rate they aim for and when they are
 * available, and a starter recruitment process saved as a template; then a first thing to do. Skipping or finishing
 * ends them for good.
 */
@Component({
  selector: 'app-onboarding',
  imports: [
    CommonModule, ReactiveFormsModule, MatStepperModule, MatButtonModule, MatChipsModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatProgressSpinnerModule, MatRadioModule, TranslateModule
  ],
  templateUrl: './onboarding.component.html',
  styleUrls: ['./onboarding.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class OnboardingComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly users = inject(UserManagementService);
  private readonly templates = inject(PipelineTemplateService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly notifications = inject(NotificationService);
  private readonly destroy$ = new Subject<void>();

  readonly processes = STARTER_PROCESSES;
  readonly vertical$ = inject(BreakpointObserver).observe('(max-width: 720px)').pipe(map(state => state.matches));

  readonly aboutForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    position: ['', Validators.maxLength(100)],
    address: ['', Validators.maxLength(255)],
    city: ['', Validators.maxLength(100)]
  });
  readonly rateForm = this.fb.group({
    targetDailyRate: this.fb.control<number | null>(null, [Validators.min(1), Validators.max(100000), Validators.pattern(/^\d+$/)]),
    availabilityDate: this.fb.control<string | null>(null)
  });
  readonly processForm = this.fb.nonNullable.group({ process: 'ESN' as StarterProcess['id'] | 'NONE' });

  skills: string[] = [];
  firstName = '';
  isLoading = true;
  isSaving = false;
  private profile?: UserProfile;
  /** The starter process already saved, so going back and forth does not save it twice. */
  private savedProcess?: StarterProcess['id'];

  ngOnInit(): void {
    this.firstName = this.auth.getUser()?.firstName ?? '';
    this.users.getUserProfile().pipe(takeUntil(this.destroy$)).subscribe({
      next: profile => {
        this.profile = profile;
        this.firstName = profile.firstName || this.firstName;
        this.aboutForm.patchValue({
          firstName: profile.firstName ?? '', lastName: profile.lastName ?? '', position: profile.position ?? '',
          address: profile.address ?? '', city: profile.city ?? ''
        });
        this.rateForm.patchValue({ targetDailyRate: profile.targetDailyRate ?? null, availabilityDate: profile.availabilityDate ?? null });
        this.skills = [...(profile.skills ?? [])];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.notifications.error('onboarding.loadError');
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Adds the typed skill on Enter or a comma, once whatever its spelling, and empties the field. */
  addSkill(input: HTMLInputElement, event?: Event): void {
    event?.preventDefault();
    const skill = input.value.trim();
    if (skill && this.skills.length < ONBOARDING_SKILLS_MAX && !this.skills.some(known => skillKey(known) === skillKey(skill))) {
      this.skills = [...this.skills, skill];
    }
    input.value = '';
  }

  removeSkill(skill: string): void {
    this.skills = this.skills.filter(known => known !== skill);
  }

  /** Saves the first two steps together, on leaving the second one. */
  saveProfile(stepper: MatStepper): void {
    if (!this.profile || this.aboutForm.invalid || this.rateForm.invalid || this.isSaving) {
      this.aboutForm.markAllAsTouched();
      this.rateForm.markAllAsTouched();
      return;
    }
    const about = this.aboutForm.getRawValue();
    const rate = this.rateForm.getRawValue();
    const update: Partial<UserProfile> = {
      ...this.profile,
      firstName: about.firstName.trim(),
      lastName: about.lastName.trim(),
      position: about.position.trim() || undefined,
      address: about.address.trim() || undefined,
      city: about.city.trim() || undefined,
      skills: this.skills,
      targetDailyRate: rate.targetDailyRate === null || `${rate.targetDailyRate}` === '' ? null : Number(rate.targetDailyRate),
      availabilityDate: rate.availabilityDate || null
    };
    this.isSaving = true;
    this.users.updateUserProfile(update).pipe(takeUntil(this.destroy$)).subscribe({
      next: saved => {
        this.profile = saved;
        this.firstName = saved.firstName;
        this.isSaving = false;
        stepper.next();
      },
      error: () => {
        this.isSaving = false;
        this.notifications.error('onboarding.saveError');
      }
    });
  }

  /** Saves the chosen starter process as a template, once; a name already taken means it is there. */
  saveProcess(stepper: MatStepper): void {
    const chosen = this.processes.find(process => process.id === this.processForm.getRawValue().process);
    if (!chosen || chosen.id === this.savedProcess) {
      stepper.next();
      return;
    }
    if (this.isSaving) {
      return;
    }
    this.isSaving = true;
    const translate = (key: string): string => this.translate.instant(key);
    this.templates.createFromSteps(translate(chosen.nameKey), starterSteps(chosen, translate)).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => this.processSaved(chosen, stepper),
      error: (error: HttpErrorResponse) => {
        if (error.status === 409) {
          this.processSaved(chosen, stepper);
          return;
        }
        this.isSaving = false;
        this.notifications.error('onboarding.processError');
      }
    });
  }

  /** Ends the welcome steps and opens the page chosen to start with. */
  finish(route: string): void {
    if (this.isSaving) {
      return;
    }
    this.isSaving = true;
    this.auth.completeOnboarding().pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.isSaving = false;
        this.router.navigateByUrl(route);
      },
      error: () => {
        this.isSaving = false;
        this.notifications.error('onboarding.finishError');
      }
    });
  }

  private processSaved(process: StarterProcess, stepper: MatStepper): void {
    this.savedProcess = process.id;
    this.isSaving = false;
    stepper.next();
  }
}
