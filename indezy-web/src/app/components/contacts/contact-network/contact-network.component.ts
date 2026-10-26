import { ChangeDetectionStrategy, Component, Input, OnChanges, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { ContactDto, ContactRelation } from '../../../models/contact.models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';

export const RELATION_NOTE_MAX = 100;

/**
 * Where a contact stands among the people the freelancer knows: whom it reports to, who reports to it, and whom it
 * works with at other companies (an ESN's account manager and the client's lead), each link with how they work together.
 */
@Component({
  selector: 'app-contact-network',
  imports: [ReactiveFormsModule, RouterModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './contact-network.component.html',
  styleUrls: ['./contact-network.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ContactNetworkComponent implements OnChanges {
  @Input({ required: true }) contact!: ContactDto;

  private readonly contactService = inject(ContactService);
  private readonly notificationService = inject(NotificationService);

  readonly noteMax = RELATION_NOTE_MAX;
  readonly form = inject(FormBuilder).nonNullable.group({
    // Not required: the form is submitted on each link, and an empty choice after it is no error.
    otherId: [null as number | null],
    note: ['', Validators.maxLength(RELATION_NOTE_MAX)]
  });

  relations: ContactRelation[] = [];
  team: ContactDto[] = [];
  /** The workspace's other contacts not linked to this one yet, by name. */
  candidates: ContactDto[] = [];
  private everyone: ContactDto[] = [];
  saving = false;

  ngOnChanges(): void {
    this.contactService.getForCurrentFreelance().subscribe({
      next: contacts => {
        this.everyone = [...contacts].sort((a, b) => this.nameOf(a).localeCompare(this.nameOf(b)));
        this.team = this.everyone.filter(other => other.managerId === this.contact.id);
        this.refreshCandidates();
      },
      error: () => this.notificationService.error('contacts.network.loadError')
    });
    this.loadRelations();
  }

  nameOf(contact: ContactDto): string {
    return [contact.firstName, contact.lastName].filter(Boolean).join(' ');
  }

  link(): void {
    const { otherId, note } = this.form.getRawValue();
    if (otherId === null || this.form.invalid || this.saving) {
      return;
    }
    this.saving = true;
    this.contactService.link(this.contact.id!, otherId, note.trim() || undefined).subscribe({
      next: () => {
        this.saving = false;
        this.form.reset({ otherId: null, note: '' });
        this.loadRelations();
      },
      error: () => {
        this.saving = false;
        this.notificationService.error('contacts.network.linkError');
      }
    });
  }

  unlink(relation: ContactRelation): void {
    this.contactService.unlink(this.contact.id!, relation.otherId).subscribe({
      next: () => this.loadRelations(),
      error: () => this.notificationService.error('contacts.network.unlinkError')
    });
  }

  private loadRelations(): void {
    this.contactService.getRelations(this.contact.id!).subscribe({
      next: relations => {
        this.relations = relations;
        this.refreshCandidates();
      },
      error: () => this.notificationService.error('contacts.network.loadError')
    });
  }

  private refreshCandidates(): void {
    const linked = new Set(this.relations.map(relation => relation.otherId));
    this.candidates = this.everyone.filter(other => other.id !== this.contact.id && !linked.has(other.id!));
  }
}
