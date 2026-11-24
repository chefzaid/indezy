import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';
import { CatalogTraining, OfferSkillsGap, Training, TrainingDetails } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { TrainingService } from '../../../services/training/training.service';
import { TrainingDialogComponent } from '../../compliance/training-dialog/training-dialog.component';

/**
 * The skills the open offers matching the profile ask for that it lacks, by share of those offers (see
 * docs/features.md#skills-gap), each with the CPF-eligible trainings of the Mon Compte Formation catalogue on request,
 * one planned in a click, and a link to plan a training; hidden when it cannot be read.
 */
@Component({
  selector: 'app-offer-skills-gap',
  imports: [RouterLink, MatButtonModule, TranslateModule],
  template: `
    @if (gap; as gap) {
      <section class="skills-gap" data-skills-gap aria-labelledby="skills-gap-title">
        <h3 id="skills-gap-title">{{ 'offers.skillsGap.title' | translate }}</h3>
        @if (gap.gaps.length) {
          <p class="intro">{{ 'offers.skillsGap.intro' | translate:{ count: gap.matchingOffers } }}</p>
          <ul>
            @for (item of gap.gaps; track item.skill) {
              <li data-skills-gap-item>
                <span class="skill">{{ item.skill }}</span>
                <span class="bar" aria-hidden="true"><span [style.width.%]="item.percent"></span></span>
                <span class="share">{{ 'offers.skillsGap.share' | translate:{ percent: item.percent, offers: item.offers } }}</span>
                <button mat-button type="button" class="cpf" (click)="toggle(item.skill)" [attr.aria-expanded]="open === item.skill"
                  [attr.data-skills-gap-cpf]="item.skill">{{ 'offers.skillsGap.cpf' | translate }}</button>
                @if (open === item.skill) {
                  <div class="trainings" [attr.data-skills-gap-trainings]="item.skill">
                    @switch (state(item.skill)) {
                      @case ('loading') { <p class="intro">{{ 'common.loading' | translate }}</p> }
                      @case ('error') { <p class="intro" role="alert" data-skills-gap-cpf-error>{{ 'offers.skillsGap.cpfError' | translate }}</p> }
                      @default {
                        @for (training of found(item.skill); track training.id) {
                          <div class="training" [attr.data-catalog-training]="training.id">
                            <span class="title">{{ training.title }}</span>
                            <span class="meta">{{ training.provider }}
                              @if (training.hours) { · {{ 'offers.skillsGap.hours' | translate:{ hours: training.hours } }} }
                              @if (training.price) { · {{ training.price }} € }
                              · {{ training.remote ? ('offers.skillsGap.remote' | translate) : training.regions.join(', ') }}</span>
                            <button mat-stroked-button type="button" (click)="plan(training)" [attr.data-catalog-plan]="training.id">
                              {{ 'offers.skillsGap.plan' | translate }}</button>
                          </div>
                        } @empty {
                          <p class="intro" data-skills-gap-cpf-none>{{ 'offers.skillsGap.cpfNone' | translate }}</p>
                        }
                        <p class="source">{{ 'offers.skillsGap.cpfSource' | translate }}</p>
                      }
                    }
                  </div>
                }
              </li>
            }
          </ul>
          <a mat-stroked-button routerLink="/documents" data-skills-gap-training>{{ 'offers.skillsGap.training' | translate }}</a>
        } @else if (gap.matchingOffers === 0) {
          <p class="intro">{{ 'offers.skillsGap.noSkills' | translate }}</p>
          <a mat-button routerLink="/profile">{{ 'offers.skillsGap.profile' | translate }}</a>
        } @else {
          <p class="intro">{{ 'offers.skillsGap.none' | translate:{ count: gap.matchingOffers } }}</p>
        }
      </section>
    }
  `,
  styles: [`
    .skills-gap { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--app-border); }
    h3 { margin: 0 0 4px; font-size: 1rem; }
    .intro { margin: 0 0 8px; color: var(--mat-sys-on-surface-variant); font-size: .875rem; }
    ul { list-style: none; margin: 0 0 12px; padding: 0; display: grid; gap: 6px; }
    li { display: grid; grid-template-columns: minmax(80px, 1fr) 2fr auto auto; align-items: center; gap: 8px; font-size: .875rem; }
    .trainings { grid-column: 1 / -1; display: grid; gap: 8px; padding: 4px 0 8px; }
    .training { display: grid; grid-template-columns: 1fr auto; gap: 2px 8px; align-items: center; }
    .training .title { font-weight: 500; overflow-wrap: anywhere; }
    .training .meta { grid-column: 1; color: var(--mat-sys-on-surface-variant); overflow-wrap: anywhere; }
    .training button { grid-column: 2; grid-row: 1 / span 2; }
    .source { margin: 0; color: var(--mat-sys-on-surface-variant); font-size: .75rem; }
    .skill { font-weight: 500; overflow-wrap: anywhere; }
    .bar { height: 8px; border-radius: 4px; background: var(--mat-sys-surface-container-high); overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--mat-sys-primary); }
    .share { color: var(--mat-sys-on-surface-variant); white-space: nowrap; }
    @media (max-width: 480px) {
      li { grid-template-columns: 1fr auto; }
      .bar { grid-column: 1 / -1; grid-row: 2; }
      .cpf { grid-column: 1 / -1; justify-self: start; }
      .training { grid-template-columns: 1fr; }
      .training button { grid-column: 1; grid-row: auto; justify-self: start; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class OfferSkillsGapComponent implements OnInit {
  private readonly offers = inject(OfferService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly dialog = inject(MatDialog);
  private readonly trainingService = inject(TrainingService);
  private readonly notifications = inject(NotificationService);

  gap: OfferSkillsGap | null = null;
  /** The skill whose trainings are shown, one at a time. */
  open: string | null = null;
  private readonly trainings = new Map<string, CatalogTraining[] | 'loading' | 'error'>();

  /** Shows a skill's CPF trainings, read once; a second click hides them. */
  toggle(skill: string): void {
    this.open = this.open === skill ? null : skill;
    if (this.open && (!this.trainings.has(skill) || this.trainings.get(skill) === 'error')) {
      this.trainings.set(skill, 'loading');
      this.offers.skillTrainings(skill).subscribe({
        next: found => {
          this.trainings.set(skill, found);
          this.changes.markForCheck();
        },
        error: () => {
          this.trainings.set(skill, 'error');
          this.changes.markForCheck();
        }
      });
      this.changes.markForCheck();
    }
  }

  state(skill: string): 'loading' | 'error' | 'found' {
    const value = this.trainings.get(skill);
    return value === 'loading' || value === 'error' ? value : 'found';
  }

  found(skill: string): CatalogTraining[] {
    const value = this.trainings.get(skill);
    return Array.isArray(value) ? value : [];
  }

  /** Opens the training form filled with the catalogue's training, saved to the trainings once confirmed. */
  plan(training: CatalogTraining): void {
    const draft = { title: training.title ?? '', provider: training.provider, hours: training.hours, cost: training.price,
      certification: training.certification, notes: 'CPF' } as unknown as Training;
    this.dialog.open<TrainingDialogComponent, Training | null, TrainingDetails>(TrainingDialogComponent,
      { data: draft, width: '560px', autoFocus: 'first-tabbable', maxWidth: '95vw' }).afterClosed().pipe(
      filter((details): details is TrainingDetails => !!details),
      switchMap(details => this.trainingService.create(details))
    ).subscribe({
      next: () => this.notifications.success('offers.skillsGap.planned'),
      error: () => this.notifications.error('offers.skillsGap.planError')
    });
  }

  ngOnInit(): void {
    this.offers.skillsGap().subscribe({
      next: gap => {
        this.gap = gap;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }
}
