import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkPost, REACTIONS, Reaction, ReactionCount } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** How long a press or a hover lasts before the other reactions show. */
export const HOLD_MS = 500;
const POP_MS = 400;

/**
 * A post's Like: a press likes it (or takes the reaction back) at once, the count changing before the server answers
 * and coming back if it refuses; a long press, a hover or the up arrow offers the other reactions.
 */
@Component({
  selector: 'app-reaction-button',
  imports: [MatIconModule, MatMenuModule, TranslateModule],
  template: `
    <span class="anchor" [matMenuTriggerFor]="reactionMenu" #trigger="matMenuTrigger" [matMenuTriggerRestoreFocus]="false"
      (menuClosed)="closed()" aria-hidden="true"></span>
    <button #button type="button" class="action like" [class.on]="post.myReaction" [attr.data-reaction]="post.myReaction"
      [attr.aria-pressed]="!!post.myReaction" (click)="press()" (mouseenter)="hold()" (mouseleave)="release()" (touchstart)="hold(true)" (touchend)="release()" (touchcancel)="release()"
      (keydown.arrowup)="open($event)" aria-keyshortcuts="ArrowUp" [attr.aria-description]="'network.reactions.more' | translate" data-like>
      @if (post.myReaction && post.myReaction !== 'LIKE') {
        <span class="emoji" [class.pop]="popping" aria-hidden="true">{{ emoji(post.myReaction) }}</span>
      } @else {
        <mat-icon [class.pop]="popping">{{ post.myReaction ? 'favorite' : 'favorite_border' }}</mat-icon>
      }
      <span class="label">{{ (post.myReaction ? 'network.reactions.' + post.myReaction : 'network.post.like') | translate }}</span>
    </button>
    <mat-menu #reactionMenu="matMenu" class="reaction-menu" yPosition="above" [attr.aria-label]="'network.reactions.title' | translate">
      @for (item of reactions; track item.reaction) {
        <button mat-menu-item type="button" (click)="react(item.reaction)" [attr.aria-label]="'network.reactions.' + item.reaction | translate"
          [attr.aria-current]="post.myReaction === item.reaction" [attr.data-react]="item.reaction">
          <span class="reaction-emoji" aria-hidden="true">{{ item.emoji }}</span>
        </button>
      }
    </mat-menu>
  `,
  styleUrls: ['./reaction-button.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ReactionButtonComponent implements OnDestroy {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private timer: ReturnType<typeof setTimeout> | undefined;
  /** A long press opened the reactions: the click that ends it does not like the post too. */
  private held = false;

  @Input({ required: true }) post!: NetworkPost;
  /** The post as it now reads: first as expected, then as the server says. */
  @Output() readonly changed = new EventEmitter<NetworkPost>();
  @ViewChild('trigger') trigger?: MatMenuTrigger;
  @ViewChild('button') button?: ElementRef<HTMLButtonElement>;
  readonly reactions = REACTIONS;
  popping = false;

  /** The post once the reader reacts this way, or takes their reaction back (null). */
  static applied(post: NetworkPost, reaction: Reaction | null): NetworkPost {
    const counts = new Map<Reaction, number>((post.reactions ?? []).map(item => [item.reaction, item.count]));
    if (post.myReaction) {
      counts.set(post.myReaction, (counts.get(post.myReaction) ?? 1) - 1);
    }
    if (reaction) {
      counts.set(reaction, (counts.get(reaction) ?? 0) + 1);
    }
    const reactions: ReactionCount[] = [...counts.entries()].filter(([, count]) => count > 0)
      .map(([kind, count]) => ({ reaction: kind, count })).sort((first, second) => second.count - first.count);
    const likes = post.likes + (reaction ? 1 : 0) - (post.myReaction ? 1 : 0);
    return { ...post, likes, liked: reaction !== null, myReaction: reaction, reactions };
  }

  emoji(reaction: Reaction): string {
    return REACTIONS.find(item => item.reaction === reaction)!.emoji;
  }

  /** A press likes the post, or takes the reaction back; the click ending a long press does neither. */
  press(): void {
    this.release();
    if (this.held) {
      this.held = false;
      return;
    }
    this.react(this.post.myReaction ? null : 'LIKE');
  }

  /** Back to the button once the reactions close, for the keyboard. */
  closed(): void {
    this.button?.nativeElement.focus({ preventScroll: true });
  }

  /** Starts counting a hover or a long press; at its end the reactions show. */
  hold(touch = false): void {
    this.release();
    this.timer = setTimeout(() => {
      this.held = touch;
      this.trigger?.openMenu();
    }, HOLD_MS);
  }

  release(): void {
    clearTimeout(this.timer);
  }

  /** The up arrow on the button shows the reactions, the first one focused. */
  open(event: Event): void {
    // The overlay would read this key too, moving the focus up from the first reaction to the last.
    event.preventDefault();
    event.stopPropagation();
    this.trigger?.openMenu();
    this.trigger?.menu?.focusFirstItem('keyboard');
  }

  react(reaction: Reaction | null): void {
    const before = this.post;
    this.post = ReactionButtonComponent.applied(before, reaction);
    this.changed.emit(this.post);
    if (reaction) {
      this.popping = true;
      setTimeout(() => this.popping = false, POP_MS);
    }
    this.network.react(before.id, reaction).subscribe({
      next: post => {
        this.post = post;
        this.changed.emit(post);
      },
      error: () => {
        this.post = before;
        this.changed.emit(before);
        this.notifications.error('network.post.likeError');
      }
    });
  }

  ngOnDestroy(): void {
    this.release();
  }
}
