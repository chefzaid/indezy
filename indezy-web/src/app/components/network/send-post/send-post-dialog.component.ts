import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { Conversation, MemberCard, NetworkPost } from '../../../models';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkService } from '../../../services/network/network.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';

/** How many conversations a post goes to at most. */
export const SEND_ROOM = 10;

/** Where the post may go: a conversation already open, or a member the reader can write to (a new conversation). */
export interface SendTarget {
  key: string;
  label: string;
  conversation: Conversation | null;
  member: MemberCard | null;
}

/** A post's address in the app, as a message carries it (the conversation shows it as the post's card). */
export function postLink(id: number): string {
  return `${location.origin}/network/posts/${id}`;
}

/**
 * **Send** a post in a message, as on LinkedIn: to one or more of the reader's conversations (with a member or a group)
 * or members they can write to, with a word if wanted; each receives the post's link, shown as its card. Answers how
 * many it went to.
 */
@Component({
  selector: 'app-send-post-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule,
    TranslateModule, MemberAvatarComponent],
  templateUrl: './send-post-dialog.component.html',
  styleUrls: ['./send-post-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class SendPostDialogComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly messages = inject(NetworkMessagesService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly dialogRef = inject<MatDialogRef<SendPostDialogComponent, number>>(MatDialogRef);
  readonly post = inject<NetworkPost>(MAT_DIALOG_DATA);

  readonly note = new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) });
  readonly filter = new FormControl('', { nonNullable: true });
  readonly room = SEND_ROOM;
  targets: SendTarget[] | null = null;
  readonly chosen = new Set<string>();
  sending = false;
  failed = false;

  static open(dialog: MatDialog, post: NetworkPost): Observable<number | undefined> {
    return dialog.open<SendPostDialogComponent, NetworkPost, number>(SendPostDialogComponent, { data: post, width: '520px', maxWidth: '95vw' })
      .afterClosed();
  }

  ngOnInit(): void {
    const members = this.network.membership(true).pipe(
      switchMap(membership => forkJoin([this.network.followers(membership.handle), this.network.following(membership.handle)])),
      map(([followers, following]) => [...new Map([...followers, ...following].map(member => [member.handle, member])).values()]),
      catchError(() => of([] as MemberCard[])));
    forkJoin([this.messages.conversations(false, true).pipe(catchError(() => of([] as Conversation[]))), members])
      .subscribe(([conversations, reachable]) => {
        const open = conversations.filter(conversation => conversation.closed === null);
        const talking = new Set(open.map(conversation => conversation.with?.handle).filter((handle): handle is string => !!handle));
        this.targets = [
          ...open.map(conversation => ({ key: `c${conversation.id}`, conversation, member: conversation.with,
            label: conversation.group?.title ?? `${conversation.with?.firstName ?? ''} ${conversation.with?.lastName ?? ''}`.trim() })),
          ...reachable.filter(member => !talking.has(member.handle))
            .sort((a, b) => `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`))
            .map(member => ({ key: `m${member.handle}`, conversation: null, member, label: `${member.firstName} ${member.lastName}` }))];
        this.changes.markForCheck();
      });
  }

  /** The targets whose name holds what is typed in the filter. */
  get shown(): SendTarget[] {
    const typed = this.filter.value.trim().toLowerCase();
    return (this.targets ?? []).filter(target => !typed || target.label.toLowerCase().includes(typed) ||
      (target.member?.handle ?? '').includes(typed));
  }

  toggle(target: SendTarget): void {
    if (this.chosen.has(target.key)) {
      this.chosen.delete(target.key);
    } else if (this.chosen.size < SEND_ROOM) {
      this.chosen.add(target.key);
    }
  }

  /** Sends the post to every target chosen, opening a conversation with a member when none is; closes with how many it reached. */
  send(): void {
    const chosen = (this.targets ?? []).filter(target => this.chosen.has(target.key));
    if (!chosen.length || this.note.invalid || this.sending) {
      return;
    }
    const note = this.note.value.trim();
    const text = note ? `${note}\n${postLink(this.post.id)}` : postLink(this.post.id);
    this.sending = true;
    this.failed = false;
    forkJoin(chosen.map(target => (target.conversation ? of(target.conversation) : this.messages.open(target.member!.handle)).pipe(
      switchMap(conversation => this.messages.send(conversation.id, text)), map(() => true), catchError(() => of(false)))))
      .subscribe(results => {
        const sent = results.filter(Boolean).length;
        this.sending = false;
        if (sent === results.length) {
          this.dialogRef.close(sent);
        } else {
          this.failed = true;
          this.changes.markForCheck();
        }
      });
  }
}
