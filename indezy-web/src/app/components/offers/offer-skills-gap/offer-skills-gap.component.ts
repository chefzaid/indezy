import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';
import { OfferSkillsGap } from '../../../models';
import { OfferService } from '../../../services/offer/offer.service';

/**
 * The skills the open offers matching the profile ask for that it lacks, by share of those offers (see
 * docs/features.md#skills-gap), with a link to plan a training; hidden when it cannot be read.
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
    li { display: grid; grid-template-columns: minmax(80px, 1fr) 2fr auto; align-items: center; gap: 8px; font-size: .875rem; }
    .skill { font-weight: 500; overflow-wrap: anywhere; }
    .bar { height: 8px; border-radius: 4px; background: var(--mat-sys-surface-container-high); overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--mat-sys-primary); }
    .share { color: var(--mat-sys-on-surface-variant); white-space: nowrap; }
    @media (max-width: 480px) { li { grid-template-columns: 1fr auto; } .bar { grid-column: 1 / -1; grid-row: 2; } }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class OfferSkillsGapComponent implements OnInit {
  private readonly offers = inject(OfferService);
  private readonly changes = inject(ChangeDetectorRef);

  gap: OfferSkillsGap | null = null;

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
