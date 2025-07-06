import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { DateRangeFilterConfig, DateRange } from '../../../models/filter.models';

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
  templateUrl: './date-range-filter.component.html',
  styleUrls: ['./date-range-filter.component.scss']
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
