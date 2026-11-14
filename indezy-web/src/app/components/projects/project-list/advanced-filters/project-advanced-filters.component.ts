import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

/** The opportunities list's advanced filters: start and end dates, client and technologies, on the list's filter form. */
@Component({
  selector: 'app-project-advanced-filters',
  imports: [ReactiveFormsModule, MatChipsModule, MatDatepickerModule, MatDividerModule, MatExpansionModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './project-advanced-filters.component.html',
  styleUrl: './project-advanced-filters.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProjectAdvancedFiltersComponent {
  @Input({ required: true }) form!: FormGroup;

  readonly techStackOptions = [
    'Angular', 'React', 'Vue.js', 'Node.js', 'Python', 'Java', 'C#', 'PHP',
    'PostgreSQL', 'MongoDB', 'MySQL', 'Docker', 'Kubernetes', 'AWS', 'Azure'
  ];

  private get selectedTechStack(): string[] {
    return this.form.get('selectedTechStack')?.value || [];
  }

  addTechStack(tech: string): void {
    if (!this.selectedTechStack.includes(tech)) {
      this.form.get('selectedTechStack')?.setValue([...this.selectedTechStack, tech]);
    }
  }

  removeTechStack(tech: string): void {
    if (this.selectedTechStack.includes(tech)) {
      this.form.get('selectedTechStack')?.setValue(this.selectedTechStack.filter(t => t !== tech));
    }
  }
}
