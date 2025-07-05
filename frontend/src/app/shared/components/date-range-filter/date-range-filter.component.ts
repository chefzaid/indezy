import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

export interface DateRangeFilterConfig {
  fromLabel?: string;
  toLabel?: string;
  fromPlaceholder?: string;
  toPlaceholder?: string;
}

export interface DateRange {
  from: Date | null;
  to: Date | null;
}

@Component({
  selector: 'app-date-range-filter',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatButtonModule
  ],
  template: `
    <div class="date-range-filter" [formGroup]="dateForm">
      <div class="date-fields">
        <mat-form-field appearance="outline" class="date-field">
          <mat-label>{{ config.fromLabel || 'Date de début' }}</mat-label>
          <input matInput 
                 [matDatepicker]="fromPicker" 
                 formControlName="from"
                 [placeholder]="config.fromPlaceholder || 'Sélectionner une date'">
          <mat-datepicker-toggle matIconSuffix [for]="fromPicker"></mat-datepicker-toggle>
          <mat-datepicker #fromPicker></mat-datepicker>
        </mat-form-field>

        <mat-form-field appearance="outline" class="date-field">
          <mat-label>{{ config.toLabel || 'Date de fin' }}</mat-label>
          <input matInput 
                 [matDatepicker]="toPicker" 
                 formControlName="to"
                 [placeholder]="config.toPlaceholder || 'Sélectionner une date'">
          <mat-datepicker-toggle matIconSuffix [for]="toPicker"></mat-datepicker-toggle>
          <mat-datepicker #toPicker></mat-datepicker>
        </mat-form-field>
      </div>

      <div class="date-actions" *ngIf="hasValues()">
        <button mat-icon-button 
                (click)="clearDates()"
                matTooltip="Effacer les dates"
                type="button">
          <mat-icon>clear</mat-icon>
        </button>
      </div>
    </div>
  `,
  styles: [`
    .date-range-filter {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;

      .date-fields {
        display: flex;
        gap: 16px;
        flex: 1;

        .date-field {
          flex: 1;
          min-width: 150px;
        }
      }

      .date-actions {
        display: flex;
        align-items: center;
      }

      @media (max-width: 768px) {
        flex-direction: column;
        align-items: stretch;

        .date-fields {
          flex-direction: column;
          gap: 12px;

          .date-field {
            width: 100%;
          }
        }

        .date-actions {
          justify-content: center;
          margin-top: 8px;
        }
      }
    }
  `]
})
export class DateRangeFilterComponent implements OnInit {
  @Input() config: DateRangeFilterConfig = {};
  @Input() initialRange: DateRange = { from: null, to: null };
  @Output() rangeChange = new EventEmitter<DateRange>();

  dateForm: FormGroup;

  constructor(private fb: FormBuilder) {
    this.dateForm = this.fb.group({
      from: [null],
      to: [null]
    });
  }

  ngOnInit(): void {
    // Set initial values
    if (this.initialRange.from) {
      this.dateForm.get('from')?.setValue(this.initialRange.from);
    }
    if (this.initialRange.to) {
      this.dateForm.get('to')?.setValue(this.initialRange.to);
    }

    // Setup value changes
    this.dateForm.valueChanges.subscribe(value => {
      this.rangeChange.emit({
        from: value.from,
        to: value.to
      });
    });
  }

  clearDates(): void {
    this.dateForm.reset();
  }

  hasValues(): boolean {
    const values = this.dateForm.value;
    return values.from || values.to;
  }
}
