import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MultiSelectOption, MultiSelectFilterConfig } from '../../../models/filter.models';

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
  templateUrl: './multi-select-filter.component.html',
  styleUrls: ['./multi-select-filter.component.scss']
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
