import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

export interface MultiSelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface MultiSelectFilterConfig {
  label?: string;
  placeholder?: string;
  maxSelections?: number;
  allowCustomValues?: boolean;
}

@Component({
  selector: 'app-multi-select-filter',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatChipsModule,
    MatIconModule,
    MatButtonModule
  ],
  template: `
    <div class="multi-select-filter" [formGroup]="selectForm">
      <mat-form-field appearance="outline" class="select-field">
        <mat-label>{{ config.label || 'Sélectionner' }}</mat-label>
        <mat-select formControlName="selection"
                    [placeholder]="config.placeholder || 'Choisir des options'"
                    (selectionChange)="onSelectionChange($event.value)">
          <mat-option *ngFor="let option of availableOptions" 
                     [value]="option.value"
                     [disabled]="option.disabled">
            {{ option.label }}
          </mat-option>
        </mat-select>
      </mat-form-field>

      <div class="selected-items" *ngIf="selectedValues.length > 0">
        <mat-chip-listbox>
          <mat-chip-option *ngFor="let value of selectedValues"
                           (removed)="removeSelection(value)">
            {{ getOptionLabel(value) }}
            <mat-icon matChipRemove>cancel</mat-icon>
          </mat-chip-option>
        </mat-chip-listbox>
      </div>

      <div class="filter-actions" *ngIf="selectedValues.length > 0">
        <button mat-button 
                (click)="clearAll()"
                class="clear-button">
          <mat-icon>clear_all</mat-icon>
          Tout effacer
        </button>
      </div>
    </div>
  `,
  styles: [`
    .multi-select-filter {
      width: 100%;

      .select-field {
        width: 100%;
        margin-bottom: 8px;
      }

      .selected-items {
        margin-bottom: 12px;

        mat-chip-listbox {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;

          mat-chip-option {
            background-color: #e3f2fd;
            color: #1976d2;
            border: 1px solid #bbdefb;

            mat-icon {
              color: #1976d2;
            }

            &:hover {
              background-color: #bbdefb;
            }
          }
        }
      }

      .filter-actions {
        display: flex;
        justify-content: flex-end;

        .clear-button {
          color: #f44336;
          font-size: 12px;
          min-height: 32px;
          padding: 0 12px;
        }
      }
    }
  `]
})
export class MultiSelectFilterComponent implements OnInit {
  @Input() options: MultiSelectOption[] = [];
  @Input() config: MultiSelectFilterConfig = {};
  @Input() initialValues: string[] = [];
  @Output() selectionChange = new EventEmitter<string[]>();

  selectForm: FormGroup;
  selectedValues: string[] = [];
  availableOptions: MultiSelectOption[] = [];

  constructor(private fb: FormBuilder) {
    this.selectForm = this.fb.group({
      selection: ['']
    });
  }

  ngOnInit(): void {
    this.availableOptions = [...this.options];
    this.selectedValues = [...this.initialValues];
    this.updateAvailableOptions();
  }

  onSelectionChange(value: string): void {
    if (value && !this.selectedValues.includes(value)) {
      // Check max selections limit
      if (this.config.maxSelections && this.selectedValues.length >= this.config.maxSelections) {
        return;
      }

      this.selectedValues.push(value);
      this.updateAvailableOptions();
      this.selectionChange.emit([...this.selectedValues]);
    }
    
    // Reset the select
    this.selectForm.get('selection')?.setValue('');
  }

  removeSelection(value: string): void {
    const index = this.selectedValues.indexOf(value);
    if (index >= 0) {
      this.selectedValues.splice(index, 1);
      this.updateAvailableOptions();
      this.selectionChange.emit([...this.selectedValues]);
    }
  }

  clearAll(): void {
    this.selectedValues = [];
    this.updateAvailableOptions();
    this.selectionChange.emit([]);
  }

  getOptionLabel(value: string): string {
    const option = this.options.find(opt => opt.value === value);
    return option ? option.label : value;
  }

  private updateAvailableOptions(): void {
    this.availableOptions = this.options.filter(option => 
      !this.selectedValues.includes(option.value)
    );
  }
}
