import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkMeetup, NewMeetup } from '../../../models';

/** A web address, http or https. */
const WEB = /^https?:\/\/[^\s/$.?#].\S*$/i;

/** The tags typed, separated by commas or spaces, without their #, lower-case, ten at most. */
export function parseTags(typed: string): string[] {
  const tags = typed.split(/[\s,]+/).map(tag => tag.replace(/^#/, '').trim().toLowerCase()).filter(tag => tag.length > 0 && tag.length <= 30);
  return [...new Set(tags)].slice(0, 10);
}

/** The value of a datetime-local field for a time the API gave. */
export function localInput(time: string | null): string {
  return time ? time.slice(0, 16) : '';
}

/**
 * The details of an event, to organize one or change it: its title, when it starts and ends, in a place (its address) or
 * online (its link), its tags and a description.
 */
@Component({
  selector: 'app-event-form',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSlideToggleModule, TranslateModule],
  templateUrl: './event-form.component.html',
  styleUrls: ['./event-form.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class EventFormComponent implements OnChanges {
  /** The event changed, or none to organize one. */
  @Input() initial: NetworkMeetup | null = null;
  @Input() saving = false;
  @Output() readonly saved = new EventEmitter<NewMeetup>();
  @Output() readonly closed = new EventEmitter<void>();

  readonly form = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3), Validators.maxLength(120)] }),
    startsAt: new FormControl('', { nonNullable: true, validators: Validators.required }),
    endsAt: new FormControl('', { nonNullable: true }),
    online: new FormControl(false, { nonNullable: true }),
    place: new FormControl('', { nonNullable: true, validators: Validators.maxLength(200) }),
    link: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500), Validators.pattern(WEB)] }),
    tags: new FormControl('', { nonNullable: true, validators: Validators.maxLength(300) }),
    description: new FormControl('', { nonNullable: true, validators: Validators.maxLength(2000) })
  }, { validators: (control: AbstractControl): ValidationErrors | null => EventFormComponent.check(control) });

  ngOnChanges(): void {
    const meetup = this.initial;
    this.form.reset({
      title: meetup?.title ?? '', startsAt: localInput(meetup?.startsAt ?? null), endsAt: localInput(meetup?.endsAt ?? null),
      online: meetup?.online ?? false, place: meetup?.place ?? '', link: meetup?.link ?? '', tags: (meetup?.tags ?? []).map(tag => `#${tag}`).join(' '),
      description: meetup?.description ?? ''
    });
  }

  /** In a place, its address; online, its link; and an end after the start. */
  static check(control: AbstractControl): ValidationErrors | null {
    const value = (control as FormGroup).getRawValue() as { startsAt: string; endsAt: string; online: boolean; place: string; link: string };
    const errors: ValidationErrors = {};
    if (value.online ? !value.link.trim() : !value.place.trim()) {
      errors['where'] = true;
    }
    if (value.startsAt && value.endsAt && value.endsAt <= value.startsAt) {
      errors['order'] = true;
    }
    return Object.keys(errors).length ? errors : null;
  }

  submit(): void {
    if (this.form.invalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saved.emit({
      title: value.title.trim(), description: value.description.trim() || null, startsAt: value.startsAt, endsAt: value.endsAt || null,
      online: value.online, place: value.online ? null : value.place.trim(), link: value.link.trim() || null, tags: parseTags(value.tags)
    });
  }
}
