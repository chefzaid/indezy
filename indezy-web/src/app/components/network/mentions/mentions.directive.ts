import { ComponentRef, DestroyRef, Directive, ElementRef, HostListener, Injector, OnDestroy, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ComponentPortal } from '@angular/cdk/portal';
import { OverlayRef, createFlexibleConnectedPositionStrategy, createOverlayRef, createRepositionScrollStrategy } from '@angular/cdk/overlay';
import { Observable, Subject, catchError, debounceTime, map, of, switchMap } from 'rxjs';

import { NetworkService } from '../../../services/network/network.service';
import { MentionChoice, MentionListComponent } from './mention-list.component';

/** What is being typed right before the caret: an @ or a # and what follows it. */
export interface MentionToken {
  sigil: '@' | '#';
  typed: string;
}

/** An @ or a # at the start or after a space or a bracket, then up to 40 letters, digits, dashes or underscores. */
const TOKEN = /(?:^|[\s(])([@#])([\p{L}\p{N}_-]{1,40})$/u;
export const MENTION_DELAY_MS = 150;

/**
 * On a text field: typing `@` lists the members to mention, `#` the tags in use, under the field; the arrows move
 * through them, Enter or Tab picks one (writing `@handle ` or `#tag `), Escape closes the list.
 */
@Directive({ selector: 'textarea[appMentions]' })
export class MentionsDirective implements OnDestroy {
  private readonly element = inject<ElementRef<HTMLTextAreaElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly network = inject(NetworkService);
  private readonly asked = new Subject<MentionToken | null>();
  private overlay: OverlayRef | null = null;
  private list: ComponentRef<MentionListComponent> | null = null;
  choices: MentionChoice[] = [];
  active = 0;

  /** What is being typed before the caret, if it is a mention or a tag. */
  static token(text: string, caret: number): MentionToken | null {
    const found = TOKEN.exec(text.slice(0, caret));
    return found ? { sigil: found[1] as '@' | '#', typed: found[2] } : null;
  }

  constructor() {
    const field = this.element.nativeElement;
    field.setAttribute('aria-autocomplete', 'list');
    this.asked.pipe(debounceTime(MENTION_DELAY_MS), switchMap(token => this.lookUp(token)), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(choices => this.offer(choices));
  }

  @HostListener('input')
  onInput(): void {
    const field = this.element.nativeElement;
    this.asked.next(MentionsDirective.token(field.value, field.selectionStart ?? field.value.length));
  }

  @HostListener('keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (this.choices.length === 0) {
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      const step = event.key === 'ArrowDown' ? 1 : -1;
      this.active = (this.active + step + this.choices.length) % this.choices.length;
      this.render();
    } else if (event.key === 'Enter' || event.key === 'Tab') {
      this.pick(this.choices[this.active]);
    } else if (event.key === 'Escape') {
      this.asked.next(null);
      this.offer([]);
    } else {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
  }

  @HostListener('blur')
  onBlur(): void {
    this.asked.next(null);
    this.offer([]);
  }

  /** Writes the choice in place of what was typed, and a space after it. */
  pick(choice: MentionChoice): void {
    const field = this.element.nativeElement;
    const caret = field.selectionStart ?? field.value.length;
    const token = MentionsDirective.token(field.value, caret);
    if (!token) {
      return;
    }
    const start = caret - token.typed.length - 1;
    const written = `${choice.sigil}${choice.value} `;
    field.value = field.value.slice(0, start) + written + field.value.slice(caret);
    field.setSelectionRange(start + written.length, start + written.length);
    this.offer([]);
    field.dispatchEvent(new Event('input', { bubbles: true }));
  }

  ngOnDestroy(): void {
    this.overlay?.dispose();
  }

  private lookUp(token: MentionToken | null): Observable<MentionChoice[]> {
    if (!token) {
      return of([]);
    }
    const found: Observable<MentionChoice[]> = token.sigil === '@'
      ? this.network.mentions(token.typed).pipe(map(members => members.map(member => ({ sigil: '@' as const, value: member.handle, member }))))
      : this.network.suggestTags(token.typed).pipe(map(tags => tags.map(tag => ({ sigil: '#' as const, value: tag, member: null }))));
    return found.pipe(catchError(() => of([])));
  }

  private offer(choices: MentionChoice[]): void {
    this.choices = choices;
    this.active = 0;
    const field = this.element.nativeElement;
    if (choices.length === 0) {
      this.overlay?.detach();
      this.list = null;
      field.removeAttribute('aria-controls');
      field.removeAttribute('aria-activedescendant');
      return;
    }
    if (!this.list) {
      this.overlay ??= createOverlayRef(this.injector, {
        positionStrategy: createFlexibleConnectedPositionStrategy(this.injector, this.element).withPositions([
          { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top' },
          { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom' }]),
        scrollStrategy: createRepositionScrollStrategy(this.injector)
      });
      this.list = this.overlay.attach(new ComponentPortal(MentionListComponent));
      this.list.instance.picked.subscribe(choice => this.pick(choice));
    }
    this.render();
  }

  private render(): void {
    const list = this.list!.instance;
    list.show(this.choices, this.active);
    const field = this.element.nativeElement;
    field.setAttribute('aria-controls', list.id);
    field.setAttribute('aria-activedescendant', `${list.id}-${this.active}`);
  }
}
