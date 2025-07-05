import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';

import { ProjectService, ProjectDto } from '../../../services/project.service';
import { ClientService, ClientDto } from '../../../services/client.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-project-form',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './project-form.component.html',
  styleUrls: ['./project-form.component.scss']
})
export class ProjectFormComponent implements OnInit, OnDestroy {
  projectForm: FormGroup;
  clients: ClientDto[] = [];
  isLoading = false;
  isSubmitting = false;
  isEditMode = false;
  projectId?: number;
  currentUser: any;

  workModeOptions = [
    { value: 'REMOTE', label: 'Télétravail' },
    { value: 'ONSITE', label: 'Sur site' },
    { value: 'HYBRID', label: 'Hybride' }
  ];

  ratingOptions = [
    { value: 1, label: '1 - Très mauvais' },
    { value: 2, label: '2 - Mauvais' },
    { value: 3, label: '3 - Moyen' },
    { value: 4, label: '4 - Bon' },
    { value: 5, label: '5 - Excellent' }
  ];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly projectService: ProjectService,
    private readonly clientService: ClientService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly snackBar: MatSnackBar
  ) {
    this.projectForm = this.createForm();
  }

  ngOnInit(): void {
    this.currentUser = this.authService.getUser();
    this.loadClients();
    
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      if (params['id']) {
        this.projectId = +params['id'];
        this.isEditMode = true;
        this.loadProject();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private createForm(): FormGroup {
    return this.fb.group({
      role: ['', [Validators.required, Validators.minLength(2)]],
      description: [''],
      techStack: [''],
      dailyRate: ['', [Validators.required, Validators.min(1)]],
      workMode: [''],
      remoteDaysPerMonth: [''],
      onsiteDaysPerMonth: [''],
      advantages: [''],
      startDate: [''],
      durationInMonths: ['', [Validators.min(1)]],
      orderRenewalInMonths: [''],
      daysPerYear: ['', [Validators.min(1), Validators.max(365)]],
      link: [''],
      personalRating: [''],
      notes: [''],
      clientId: ['']
    });
  }

  private loadClients(): void {
    this.clientService.getClients()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (clients) => {
          this.clients = clients.filter(client => client.status === 'ACTIVE');
        },
        error: (error) => {
          console.error('Error loading clients:', error);
          this.snackBar.open('Erreur lors du chargement des clients', 'Fermer', { duration: 3000 });
        }
      });
  }

  private loadProject(): void {
    if (!this.projectId) return;
    
    this.isLoading = true;
    this.projectService.getById(this.projectId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (project) => {
          if (project) {
            this.projectForm.patchValue({
              role: project.role,
              description: project.description,
              techStack: project.techStack,
              dailyRate: project.dailyRate,
              workMode: project.workMode,
              remoteDaysPerMonth: project.remoteDaysPerMonth,
              onsiteDaysPerMonth: project.onsiteDaysPerMonth,
              advantages: project.advantages,
              startDate: project.startDate ? new Date(project.startDate) : null,
              durationInMonths: project.durationInMonths,
              orderRenewalInMonths: project.orderRenewalInMonths,
              daysPerYear: project.daysPerYear,
              link: project.link,
              personalRating: project.personalRating,
              notes: project.notes,
              clientId: project.clientId
            });
          } else {
            this.snackBar.open('Projet non trouvé', 'Fermer', { duration: 3000 });
            this.router.navigate(['/projects']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading project:', error);
          this.snackBar.open('Erreur lors du chargement du projet', 'Fermer', { duration: 3000 });
          this.isLoading = false;
        }
      });
  }

  onSubmit(): void {
    if (this.projectForm.valid && !this.isSubmitting) {
      this.isSubmitting = true;
      const formValue = this.projectForm.value;
      
      // Find client name for the project
      const selectedClient = this.clients.find(client => client.id === formValue.clientId);
      const projectData: ProjectDto = {
        ...formValue,
        clientName: selectedClient?.name || '',
        freelanceId: this.currentUser?.id,
        startDate: formValue.startDate ? formValue.startDate.toISOString().split('T')[0] : undefined
      };

      const operation = this.isEditMode
        ? this.projectService.update(this.projectId!, projectData)
        : this.projectService.create(projectData);

      operation.pipe(takeUntil(this.destroy$)).subscribe({
        next: () => {
          const message = this.isEditMode ? 'Projet modifié avec succès' : 'Projet créé avec succès';
          this.snackBar.open(message, 'Fermer', { duration: 3000 });
          this.router.navigate(['/projects']);
        },
        error: (error) => {
          console.error('Error saving project:', error);
          const message = this.isEditMode ? 'Erreur lors de la modification du projet' : 'Erreur lors de la création du projet';
          this.snackBar.open(message, 'Fermer', { duration: 3000 });
          this.isSubmitting = false;
        }
      });
    } else {
      this.markFormGroupTouched();
    }
  }

  onCancel(): void {
    this.router.navigate(['/projects']);
  }

  private markFormGroupTouched(): void {
    Object.keys(this.projectForm.controls).forEach(key => {
      const control = this.projectForm.get(key);
      control?.markAsTouched();
    });
  }

  getFieldError(fieldName: string): string {
    const control = this.projectForm.get(fieldName);
    if (control?.errors && control.touched) {
      if (control.errors['required']) {
        return 'Ce champ est requis';
      }
      if (control.errors['minlength']) {
        return `Minimum ${control.errors['minlength'].requiredLength} caractères`;
      }
      if (control.errors['min']) {
        return `La valeur doit être supérieure à ${control.errors['min'].min}`;
      }
      if (control.errors['max']) {
        return `La valeur doit être inférieure à ${control.errors['max'].max}`;
      }
    }
    return '';
  }

  get pageTitle(): string {
    return this.isEditMode ? 'Modifier le projet' : 'Nouveau projet';
  }

  get submitButtonText(): string {
    return this.isEditMode ? 'Modifier' : 'Créer';
  }
}
