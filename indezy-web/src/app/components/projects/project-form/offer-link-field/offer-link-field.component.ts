import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, ElementRef, EventEmitter, Input, Output, ViewChild,
  inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, Subscription, expand, switchMap, timer } from 'rxjs';

import { OfferLink } from '../../../../models/offer.models';
import { OfferService } from '../../../../services/offer/offer.service';

/** While its site is being read, a link is looked up again this often, for at most two minutes. */
export const LOOK_AGAIN_MS = 5_000;
export const READING_FOR_MS = 120_000;

/** What the field says under itself, in the words of `key`. */
export interface OfferLinkMessage {
  key: string;
  tone: 'success' | 'problem';
  site?: string;
  title?: string;
  /** A time to name ("can be read again at"). */
  at?: string;
  /** The opportunity to open: the mission is in the pipeline already. */
  projectId?: number;
  expired?: boolean;
}

/**
 * The link of an offer from one of the feed's sites, looked up on request (see docs/features.md#from-an-offer-link):
 * an offer found is handed to the form, anything else is told under the field.
 */
@Component({
  selector: 'app-offer-link-field',
  imports: [ReactiveFormsModule, RouterModule, DatePipe, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule],
  templateUrl: './offer-link-field.component.html',
  styleUrls: ['./offer-link-field.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferLinkFieldComponent implements AfterViewInit {
  /** Puts the cursor in the field, as when the page was opened to start from a link. */
  @Input() autofocus = false;
  /** An offer found, or a posting read from another site, not in the pipeline yet: its draft fills the form in. */
  @Output() readonly found = new EventEmitter<OfferLink>();
  @ViewChild('linkInput') private linkInput?: ElementRef<HTMLInputElement>;

  /** Optional for the project form, so not marked required: an empty field is told on "Fill in". */
  readonly link = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(2000)] });
  busy = false;
  /** The site being read for the offer. */
  readingSite?: string;
  message?: OfferLinkMessage;

  private readonly offers = inject(OfferService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private lookup?: Subscription;
  private last?: OfferLink;

  ngAfterViewInit(): void {
    if (this.autofocus) {
      this.linkInput?.nativeElement.focus();
    }
  }

  fill(): void {
    const url = this.link.value.trim();
    if (!url || this.link.invalid) {
      if (!url) {
        this.link.setErrors({ required: true });
      }
      this.link.markAsTouched();
      return;
    }
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.message = undefined;
    this.last = undefined;
    const until = Date.now() + READING_FOR_MS;
    this.lookup?.unsubscribe();
    this.lookup = this.offers.resolveLink(url).pipe(
      expand(link => link.status === 'READING' && Date.now() < until
        ? timer(LOOK_AGAIN_MS).pipe(switchMap(() => this.offers.resolveLink(url)))
        : EMPTY),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: link => this.show(link),
      error: (error: HttpErrorResponse) => this.fail(error),
      complete: () => this.done()
    });
  }

  private show(link: OfferLink): void {
    this.last = link;
    if (link.status === 'READING') {
      this.readingSite = link.siteName;
    } else {
      this.readingSite = undefined;
      this.message = messageOf(link);
      if (link.status === 'FOUND' || link.status === 'POSTING') {
        this.found.emit(link);
      }
    }
    this.changes.markForCheck();
  }

  private done(): void {
    if (this.last?.status === 'READING') {
      this.message = { key: 'projects.offerLink.stillReading', tone: 'problem', site: this.last.siteName };
    }
    this.busy = false;
    this.readingSite = undefined;
    this.changes.markForCheck();
  }

  private fail(error: HttpErrorResponse): void {
    const key = error.status === 400 ? 'invalid' : error.status === 429 ? 'tooMany' : 'error';
    this.message = { key: `projects.offerLink.${key}`, tone: 'problem' };
    this.busy = false;
    this.readingSite = undefined;
    this.changes.markForCheck();
  }
}

/** What to say of a link that was looked up. */
export function messageOf(link: OfferLink): OfferLinkMessage {
  const site = link.siteName;
  switch (link.status) {
    case 'FOUND':
      return { key: 'projects.offerLink.filled', tone: 'success', site, title: link.offer?.title, expired: link.offer?.expired };
    case 'IN_PIPELINE':
      return { key: 'projects.offerLink.status.IN_PIPELINE', tone: 'problem', site, projectId: link.projectId };
    case 'POSTING':
      return { key: 'projects.offerLink.filledFromPosting', tone: 'success', site, title: link.draft?.project.role };
    case 'NOT_COLLECTED':
      return link.retryAt
        ? { key: 'projects.offerLink.status.NOT_COLLECTED_UNTIL', tone: 'problem', site, at: link.retryAt }
        : { key: 'projects.offerLink.status.NOT_COLLECTED', tone: 'problem', site };
    default:
      return { key: `projects.offerLink.status.${link.status}`, tone: 'problem', site };
  }
}
