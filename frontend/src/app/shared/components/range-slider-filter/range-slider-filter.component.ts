import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { debounceTime, distinctUntilChanged } from 'rxjs';

export interface RangeSliderConfig {
  label?: string;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
  showInputs?: boolean;
  debounceTime?: number;
}

export interface RangeValue {
  min: number;
  max: number;
}

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
  template: `
    <div class="range-slider-filter" [formGroup]="rangeForm">
      <div class="range-header">
        <label class="range-label">{{ config.label || 'Plage de valeurs' }}</label>
        <button mat-icon-button 
                *ngIf="hasCustomValues()"
                (click)="resetRange()"
                matTooltip="Réinitialiser"
                type="button">
          <mat-icon>refresh</mat-icon>
        </button>
      </div>

      <div class="range-content">
        <div class="range-inputs" *ngIf="config.showInputs">
          <mat-form-field appearance="outline" class="range-input">
            <mat-label>Min</mat-label>
            <input matInput 
                   type="number"
                   formControlName="minInput"
                   [min]="config.min || 0"
                   [max]="config.max || 100">
            <span matTextSuffix *ngIf="config.unit">{{ config.unit }}</span>
          </mat-form-field>

          <mat-form-field appearance="outline" class="range-input">
            <mat-label>Max</mat-label>
            <input matInput 
                   type="number"
                   formControlName="maxInput"
                   [min]="config.min || 0"
                   [max]="config.max || 100">
            <span matTextSuffix *ngIf="config.unit">{{ config.unit }}</span>
          </mat-form-field>
        </div>

        <div class="slider-container">
          <mat-slider 
            [min]="config.min || 0"
            [max]="config.max || 100"
            [step]="config.step || 1"
            [discrete]="true"
            [showTickMarks]="false">
            <input matSliderStartThumb formControlName="minSlider">
            <input matSliderEndThumb formControlName="maxSlider">
          </mat-slider>
        </div>

        <div class="range-display">
          <span class="range-value">
            {{ rangeForm.get('minSlider')?.value }}{{ config.unit || '' }} - 
            {{ rangeForm.get('maxSlider')?.value }}{{ config.unit || '' }}
          </span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .range-slider-filter {
      width: 100%;
      padding: 16px;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      background-color: #fafafa;

      .range-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;

        .range-label {
          font-weight: 500;
          color: #333;
          font-size: 14px;
        }
      }

      .range-content {
        .range-inputs {
          display: flex;
          gap: 16px;
          margin-bottom: 16px;

          .range-input {
            flex: 1;
          }
        }

        .slider-container {
          margin: 16px 0;
          padding: 0 8px;

          mat-slider {
            width: 100%;
          }
        }

        .range-display {
          text-align: center;
          margin-top: 8px;

          .range-value {
            font-size: 14px;
            color: #666;
            font-weight: 500;
          }
        }
      }

      @media (max-width: 768px) {
        .range-content {
          .range-inputs {
            flex-direction: column;
            gap: 12px;
          }
        }
      }
    }
  `]
})
export class RangeSliderFilterComponent implements OnInit {
  @Input() config: RangeSliderConfig = {};
  @Input() initialRange: RangeValue = { min: 0, max: 100 };
  @Output() rangeChange = new EventEmitter<RangeValue>();

  rangeForm: FormGroup;

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
    this.rangeForm.get('minSlider')?.valueChanges.subscribe(value => {
      this.rangeForm.get('minInput')?.setValue(value, { emitEvent: false });
    });

    this.rangeForm.get('maxSlider')?.valueChanges.subscribe(value => {
      this.rangeForm.get('maxInput')?.setValue(value, { emitEvent: false });
    });

    this.rangeForm.get('minInput')?.valueChanges.subscribe(value => {
      if (value !== null && value !== undefined) {
        this.rangeForm.get('minSlider')?.setValue(value, { emitEvent: false });
      }
    });

    this.rangeForm.get('maxInput')?.valueChanges.subscribe(value => {
      if (value !== null && value !== undefined) {
        this.rangeForm.get('maxSlider')?.setValue(value, { emitEvent: false });
      }
    });

    // Emit range changes with debouncing
    this.rangeForm.valueChanges.pipe(
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
