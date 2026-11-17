import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, forkJoin, switchMap } from 'rxjs';

import { MemberCard } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';

/**
 * What the dialog asks: a new group's name and members, members to add to a group (its title and members known), or, with
 * `direct`, the one member to write to.
 */
export interface GroupRequest {
  /** The group's current name when adding members; none to create one. */
  title: string | null;
  /** The handles already in the group, left out of the choices. */
  present: string[];
  /** How many more members the group takes. */
  room: number;
  /** Picks one member to write to, the dialog closing on the choice. */
  direct?: boolean;
}

/** What the reader chose: the group's name (for a new group) and the handles of the members. */
export interface GroupChoice {
  title: string;
  handles: string[];
}

/**
 * Names a group and picks its members among the members the reader can write to (those following them or followed by
 * them), picks the members to add to one, or picks the one member a new conversation goes to.
 */
@Component({
  selector: 'app-group-dialog',
  imports: [MemberAvatarComponent, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule,
    TranslateModule],
  templateUrl: './group-dialog.component.html',
  styleUrls: ['./group-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class GroupDialogComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly dialogRef = inject<MatDialogRef<GroupDialogComponent, GroupChoice>>(MatDialogRef);
  readonly request = inject<GroupRequest>(MAT_DIALOG_DATA);

  readonly title = new FormControl(this.request.title ?? '', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] });
  readonly filter = new FormControl('', { nonNullable: true });
  /** The members the reader can write to: their followers and the members they follow, each once, by name. */
  followers: MemberCard[] | null = null;
  readonly chosen = new Set<string>();

  static open(dialog: MatDialog, request: GroupRequest): Observable<GroupChoice | undefined> {
    return dialog.open<GroupDialogComponent, GroupRequest, GroupChoice>(GroupDialogComponent, { data: request, width: '480px', maxWidth: '95vw' })
      .afterClosed();
  }

  ngOnInit(): void {
    this.network.membership().pipe(switchMap(membership => forkJoin([this.network.followers(membership.handle ?? ''),
      this.network.following(membership.handle ?? '')]))).subscribe({
      next: ([followers, following]) => {
        const byHandle = new Map([...followers, ...following].map(member => [member.handle, member]));
        this.followers = [...byHandle.values()].filter(member => !this.request.present.includes(member.handle))
          .sort((a, b) => `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`));
        this.changes.markForCheck();
      },
      error: () => {
        this.followers = [];
        this.changes.markForCheck();
      }
    });
  }

  /** The followers whose name or handle holds what was typed. */
  get shown(): MemberCard[] {
    const typed = this.filter.value.trim().toLowerCase();
    return (this.followers ?? []).filter(member => !typed || `${member.firstName} ${member.lastName} ${member.handle}`.toLowerCase().includes(typed));
  }

  toggle(handle: string, on: boolean): void {
    if (on && this.chosen.size < this.request.room) {
      this.chosen.add(handle);
    } else if (!on) {
      this.chosen.delete(handle);
    }
  }

  get valid(): boolean {
    return this.chosen.size > 0 && (this.request.title !== null || this.title.valid);
  }

  /** Writes to this member: the dialog closes with them alone. */
  pick(handle: string): void {
    this.dialogRef.close({ title: '', handles: [handle] });
  }

  done(): void {
    if (this.valid) {
      this.dialogRef.close({ title: this.title.value.trim(), handles: [...this.chosen] });
    }
  }
}
