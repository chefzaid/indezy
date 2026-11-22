import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { switchMap } from 'rxjs';
import { AiApplicationPack, AiPackChannel } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { ProjectService } from '../../../services/project/project.service';

/**
 * Prepares the application to a feed offer with AI (see docs/features.md#ai-features): the message for the channel
 * chosen, editable and copied, the screening answers and a rate to propose; **Add to pipeline with this pack** adds the
 * offer as an opportunity, the pack kept in its journal, where the tailored dossier is one click away.
 */
@Component({
  selector: 'app-offer-pack',
  imports: [ReactiveFormsModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule],
  template: `
    <section class="pack" data-offer-pack aria-labelledby="offer-pack-title">
      <h2 id="offer-pack-title">{{ 'ai.pack.title' | translate }}</h2>
      <div class="row">
        <mat-button-toggle-group [formControl]="channel" [attr.aria-label]="'ai.pack.channel' | translate">
          @for (option of channels; track option) {
            <mat-button-toggle [value]="option">{{ 'ai.pack.channels.' + option | translate }}</mat-button-toggle>
          }
        </mat-button-toggle-group>
        <button mat-flat-button color="primary" type="button" (click)="prepare()" [disabled]="preparing" data-offer-pack-write>
          @if (preparing) {
            <mat-spinner diameter="18" [attr.aria-label]="'ai.pack.busy' | translate"></mat-spinner>
          } @else {
            <mat-icon>auto_awesome</mat-icon>
          }
          {{ (pack ? 'ai.pack.again' : 'ai.pack.write') | translate }}
        </button>
      </div>
      @if (pack; as pack) {
        @if (pack.subject) {
          <p class="subject"><strong>{{ 'ai.pack.subject' | translate }}</strong> {{ pack.subject }}</p>
        }
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ 'ai.pack.message' | translate }}</mat-label>
          <textarea matInput [formControl]="message" rows="7" data-offer-pack-message></textarea>
          @if (channel.value === 'LINKEDIN') {
            <mat-hint align="end">{{ message.value.length }}/300</mat-hint>
          }
        </mat-form-field>
        <button mat-stroked-button type="button" (click)="copy()"><mat-icon>content_copy</mat-icon>{{ 'ai.pack.copy' | translate }}</button>
        @if (pack.screening.length) {
          <h3>{{ 'ai.pack.screening' | translate }}</h3>
          <dl class="screening">
            @for (item of pack.screening; track $index) {
              <div><dt>{{ item.question }}</dt><dd>{{ item.answer }}</dd></div>
            }
          </dl>
        }
        @if (pack.rate) {
          <h3>{{ 'ai.pack.rate' | translate:{ rate: pack.rate } }}</h3>
          <ul>
            @for (reason of pack.rateReasons; track $index) {
              <li>{{ reason }}</li>
            }
          </ul>
        }
        <p class="disclaimer">{{ 'ai.disclaimer' | translate }}</p>
        <button mat-stroked-button type="button" (click)="addToPipeline()" [disabled]="adding" data-offer-pack-add>
          <mat-icon>playlist_add</mat-icon>{{ 'ai.pack.add' | translate }}
        </button>
      }
    </section>
  `,
  styles: [`
    .pack { margin: 16px 0; padding: 16px; border: 1px solid var(--app-border); border-radius: var(--app-radius, 12px); }
    h2 { margin: 0 0 12px; font-size: 1.1rem; }
    h3 { margin: 16px 0 4px; font-size: 1rem; }
    .row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-bottom: 12px; }
    .full { width: 100%; }
    .subject { margin: 0 0 8px; overflow-wrap: anywhere; }
    .screening { margin: 0; display: grid; gap: 6px; }
    dt { font-weight: 500; }
    dd { margin: 0; color: var(--mat-sys-on-surface-variant); }
    ul { margin: 0; padding-left: 20px; }
    .disclaimer { margin: 12px 0; color: var(--mat-sys-on-surface-variant); font-size: .8125rem; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class OfferPackComponent {
  @Input({ required: true }) offerId!: number;

  private readonly ai = inject(AiService);
  private readonly offers = inject(OfferService);
  private readonly projects = inject(ProjectService);
  private readonly notifications = inject(NotificationService);
  private readonly clipboard = inject(Clipboard);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);

  readonly channels: AiPackChannel[] = ['ESN_EMAIL', 'MALT', 'LINKEDIN'];
  readonly channel = new FormControl<AiPackChannel>('ESN_EMAIL', { nonNullable: true });
  readonly message = new FormControl('', { nonNullable: true });
  pack: AiApplicationPack | null = null;
  preparing = false;
  adding = false;

  prepare(): void {
    this.preparing = true;
    this.ai.applicationPack(this.offerId, this.channel.value, this.translate.currentLang === 'en' ? 'EN' : 'FR').subscribe({
      next: pack => {
        this.preparing = false;
        this.pack = pack;
        this.message.setValue(pack.message);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.preparing = false;
        this.notifications.error(aiErrorKey(error));
        this.changes.markForCheck();
      }
    });
  }

  copy(): void {
    const pack = this.pack;
    const text = pack?.subject ? `${pack.subject}\n\n${this.message.value}` : this.message.value;
    if (this.clipboard.copy(text)) {
      this.notifications.success('ai.pack.copied');
    }
  }

  /** Adds the offer to the pipeline and keeps the pack in the new opportunity's journal, then opens it. */
  addToPipeline(): void {
    const pack = this.pack;
    if (!pack || this.adding) {
      return;
    }
    const t = (key: string, params?: object): string => this.translate.instant(`ai.pack.${key}`, params);
    const note = [`## ${t('title')} (${t('channels.' + this.channel.value)})`, ...(pack.subject ? [`**${t('subject')}** ${pack.subject}`] : []),
      this.message.value, ...(pack.screening.length ? [`### ${t('screening')}`,
        pack.screening.map(item => `- **${item.question}** ${item.answer}`).join('\n')] : []),
      ...(pack.rate ? [`### ${t('rate', { rate: pack.rate })}`, pack.rateReasons.map(reason => `- ${reason}`).join('\n')] : [])].join('\n\n');
    this.adding = true;
    this.offers.importOffer(this.offerId).pipe(
      switchMap(project => this.projects.addProjectNote(project.id!, note).pipe(switchMap(() => [project])))
    ).subscribe({
      next: project => {
        this.adding = false;
        this.notifications.success('ai.pack.added');
        void this.router.navigate(['/projects', project.id]);
      },
      error: () => {
        this.adding = false;
        this.notifications.error('ai.pack.addError');
        this.changes.markForCheck();
      }
    });
  }
}
