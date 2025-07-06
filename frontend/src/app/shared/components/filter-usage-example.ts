// Example usage of the Comprehensive Filter Panel Component
// This file demonstrates how to implement the advanced filter components

import { Component } from '@angular/core';
import { ComprehensiveFilterPanelComponent } from './comprehensive-filter-panel/comprehensive-filter-panel.component';
import { ComprehensiveFilterConfig, FilterValues } from '../../models/filter.models';

@Component({
  selector: 'app-example-usage',
  standalone: true,
  imports: [ComprehensiveFilterPanelComponent],
  template: `
    <div class="example-container">
      <h2>Client Management with Advanced Filters</h2>
      
      <app-comprehensive-filter-panel
        [config]="clientFilterConfig"
        [initialFilters]="initialFilters"
        (filtersChange)="onFiltersChange($event)"
        (presetApplied)="onPresetApplied($event)">
        
        <!-- Custom filter section example -->
        <div slot="custom-status" class="custom-status-filter">
          <mat-checkbox>Include archived clients</mat-checkbox>
        </div>
      </app-comprehensive-filter-panel>

      <div class="results-section">
        <h3>Current Filters:</h3>
        <pre>{{ currentFilters | json }}</pre>
      </div>
    </div>
  `
})
export class FilterUsageExampleComponent {
  currentFilters: FilterValues = {};
  initialFilters = {
    search: '',
    industries: [],
    projectCount_min: 0,
    projectCount_max: 50
  };

  // Configuration for client filters
  clientFilterConfig: ComprehensiveFilterConfig = {
    title: 'Filtres clients avancés',
    subtitle: 'Recherchez et filtrez vos clients selon vos critères',
    collapsible: true,
    initiallyExpanded: true,
    showPresets: true,
    presetsConfig: {
      allowCustomPresets: true,
      maxPresets: 10,
      storageKey: 'client-filter-presets'
    },
    sections: [
      {
        id: 'search',
        title: 'Recherche générale',
        icon: 'search',
        type: 'search',
        config: {
          label: 'Rechercher un client',
          placeholder: 'Nom, secteur, adresse, email...',
          icon: 'search',
          debounceTime: 300
        }
      },
      {
        id: 'industries',
        title: 'Secteurs d\'activité',
        icon: 'business',
        type: 'multiSelect',
        config: {
          label: 'Sélectionner les secteurs',
          placeholder: 'Choisir des secteurs',
          maxSelections: 5
        },
        options: [
          { value: 'tech', label: 'Technologie' },
          { value: 'finance', label: 'Finance' },
          { value: 'health', label: 'Santé' },
          { value: 'education', label: 'Éducation' },
          { value: 'retail', label: 'Commerce' },
          { value: 'manufacturing', label: 'Industrie' },
          { value: 'services', label: 'Services' },
          { value: 'real-estate', label: 'Immobilier' },
          { value: 'transport', label: 'Transport' },
          { value: 'energy', label: 'Énergie' }
        ]
      },
      {
        id: 'createdDate',
        title: 'Date de création',
        icon: 'date_range',
        type: 'dateRange',
        config: {
          fromLabel: 'Créé après le',
          toLabel: 'Créé avant le',
          fromPlaceholder: 'Date de début',
          toPlaceholder: 'Date de fin'
        }
      },
      {
        id: 'projectCount',
        title: 'Nombre de projets',
        icon: 'assignment',
        type: 'rangeSlider',
        config: {
          label: 'Plage de projets',
          min: 0,
          max: 100,
          step: 1,
          unit: ' projets',
          showInputs: true,
          debounceTime: 500
        }
      },
      {
        id: 'custom-status',
        title: 'Options avancées',
        icon: 'settings',
        type: 'custom'
      }
    ]
  };

  // Configuration for project filters
  projectFilterConfig: ComprehensiveFilterConfig = {
    title: 'Filtres projets',
    subtitle: 'Filtrez vos projets par critères spécifiques',
    collapsible: false,
    showPresets: true,
    presetsConfig: {
      allowCustomPresets: true,
      storageKey: 'project-filter-presets'
    },
    sections: [
      {
        id: 'search',
        title: 'Recherche',
        type: 'search',
        config: {
          label: 'Rechercher un projet',
          placeholder: 'Nom, description, technologies...',
          icon: 'search'
        }
      },
      {
        id: 'technologies',
        title: 'Technologies',
        type: 'multiSelect',
        config: {
          label: 'Technologies utilisées',
          placeholder: 'Sélectionner des technologies'
        },
        options: [
          { value: 'angular', label: 'Angular' },
          { value: 'react', label: 'React' },
          { value: 'vue', label: 'Vue.js' },
          { value: 'nodejs', label: 'Node.js' },
          { value: 'python', label: 'Python' },
          { value: 'java', label: 'Java' },
          { value: 'dotnet', label: '.NET' },
          { value: 'php', label: 'PHP' }
        ]
      },
      {
        id: 'budget',
        title: 'Budget',
        type: 'rangeSlider',
        config: {
          label: 'Plage de budget',
          min: 0,
          max: 100000,
          step: 1000,
          unit: '€',
          showInputs: true
        }
      },
      {
        id: 'timeline',
        title: 'Période du projet',
        type: 'dateRange',
        config: {
          fromLabel: 'Début du projet',
          toLabel: 'Fin du projet'
        }
      }
    ]
  };

  onFiltersChange(filters: FilterValues): void {
    this.currentFilters = filters;

    // Here you would typically:
    // 1. Apply filters to your data source
    // 2. Update the displayed results
    // 3. Save filter state if needed

    // Example:
    // this.clientService.getClients(filters).subscribe(clients => {
    //   this.filteredClients = clients;
    // });
  }

  onPresetApplied(preset: FilterValues): void {
    // Handle preset application
    console.log('Preset applied:', preset);
  }
}
