import { Component, DestroyRef, Input, Output, EventEmitter, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { RangeSliderConfig, RangeValue } from '../../../models/filter.models';

@Component({
  selector: 'app-range-slider-filter',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSliderModule,
    MatIconModule,
    MatButtonModule
  ],
  templateUrl: './range-slider-filter.component.html',
  styleUrls: ['./range-slider-filter.component.scss']
})
export class RangeSliderFilterComponent implements OnInit {
  @Input() config: RangeSliderConfig = {};
  @Input() initialRange: RangeValue = { min: 0, max: 100 };
  @Output() rangeChange = new EventEmitter<RangeValue>();

  rangeForm: FormGroup;

  private readonly destroyRef = inject(DestroyRef);
  constructor(private fb: FormBuilder) {
    this.rangeForm = this.fb.group({
      minSlider: [0],
      maxSlider: [100],
      minInput: [0],
      maxInput: [100]
    });
  }

  ngOnInit(): void {
    const min = this.initialRange.min ?? this.config.min ?? 0;
    const max = this.initialRange.max ?? this.config.max ?? 100;

    // Set initial values
    this.rangeForm.patchValue({
      minSlider: min,
      maxSlider: max,
      minInput: min,
      maxInput: max
    });

    // Sync slider and input values
    this.rangeForm.get('minSlider')?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(value => {
      this.rangeForm.get('minInput')?.setValue(value, { emitEvent: false });
    });

    this.rangeForm.get('maxSlider')?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(value => {
      this.rangeForm.get('maxInput')?.setValue(value, { emitEvent: false });
    });

    this.rangeForm.get('minInput')?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(value => {
      if (value !== null && value !== undefined) {
        this.rangeForm.get('minSlider')?.setValue(value, { emitEvent: false });
      }
    });

    this.rangeForm.get('maxInput')?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(value => {
      if (value !== null && value !== undefined) {
        this.rangeForm.get('maxSlider')?.setValue(value, { emitEvent: false });
      }
    });

    // Emit range changes with debouncing
    this.rangeForm.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef),
      debounceTime(this.config.debounceTime || 300),
      distinctUntilChanged()
    ).subscribe(value => {
      this.rangeChange.emit({
        min: value.minSlider,
        max: value.maxSlider
      });
    });
  }

  resetRange(): void {
    const min = this.config.min ?? 0;
    const max = this.config.max ?? 100;
    
    this.rangeForm.patchValue({
      minSlider: min,
      maxSlider: max,
      minInput: min,
      maxInput: max
    });
  }

  hasCustomValues(): boolean {
    const values = this.rangeForm.value;
    const defaultMin = this.config.min ?? 0;
    const defaultMax = this.config.max ?? 100;
    
    return values.minSlider !== defaultMin || values.maxSlider !== defaultMax;
  }
}
