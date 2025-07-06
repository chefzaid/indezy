import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ProjectDto {
  id?: number;
  role: string;
  description?: string;
  techStack?: string;
  dailyRate: number;
  workMode?: 'REMOTE' | 'ONSITE' | 'HYBRID';
  remoteDaysPerMonth?: number;
  onsiteDaysPerMonth?: number;
  advantages?: string;
  startDate?: string;
  durationInMonths?: number;
  orderRenewalInMonths?: number;
  daysPerYear?: number;
  documents?: string[];
  link?: string;
  personalRating?: number;
  notes?: string;
  freelanceId?: number;
  clientId?: number;
  clientName?: string;
  middlemanId?: number;
  middlemanName?: string;
  sourceId?: number;
  sourceName?: string;
  totalRevenue?: number;
  totalSteps?: number;
  completedSteps?: number;
  failedSteps?: number;
}

export interface ProjectFilters {
  minRate?: number;
  maxRate?: number;
  workMode?: 'REMOTE' | 'ONSITE' | 'HYBRID';
  startDateAfter?: string;
  techStack?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ProjectService {
  private readonly API_URL = `${environment.apiUrl}/projects`;

  constructor(private readonly http: HttpClient) {}

  getAll(): Observable<ProjectDto[]> {
    return this.http.get<ProjectDto[]>(this.API_URL);
  }

  getById(id: number): Observable<ProjectDto> {
    // Mock data for development - replace with real API call when backend is ready
    return new Observable(observer => {
      setTimeout(() => {
        const mockProjects: ProjectDto[] = [
          {
            id: 1,
            role: 'Développeur Full Stack',
            clientName: 'TechCorp',
            clientId: 1,
            dailyRate: 600,
            workMode: 'HYBRID',
            remoteDaysPerMonth: 15,
            onsiteDaysPerMonth: 5,
            techStack: 'React, Node.js, PostgreSQL',
            description: 'Développement d\'une application web moderne pour la gestion des ressources humaines. Le projet inclut la création d\'un système complet de gestion des employés, des congés, et des évaluations de performance.',
            advantages: 'Équipe dynamique, technologies modernes, possibilité de télétravail',
            startDate: '2024-01-15',
            durationInMonths: 6,
            orderRenewalInMonths: 3,
            daysPerYear: 220,
            documents: ['Contrat signé', 'Cahier des charges', 'Spécifications techniques'],
            link: 'https://techcorp.com/project-portal',
            personalRating: 4,
            notes: 'Excellent projet avec une équipe très professionnelle. Technologies intéressantes et défis techniques stimulants.',
            freelanceId: 1,
            sourceName: 'LinkedIn',
            sourceId: 1,
            totalRevenue: 72000,
            totalSteps: 5,
            completedSteps: 3,
            failedSteps: 0
          },
          {
            id: 2,
            role: 'Consultant Angular',
            clientName: 'StartupInnovante',
            clientId: 2,
            dailyRate: 550,
            workMode: 'REMOTE',
            remoteDaysPerMonth: 20,
            onsiteDaysPerMonth: 0,
            techStack: 'Angular, TypeScript, Firebase',
            description: 'Refonte complète de l\'interface utilisateur d\'une plateforme e-commerce. Migration d\'une ancienne application vers Angular 17 avec une nouvelle architecture moderne.',
            advantages: 'Flexibilité totale, startup innovante, stack technique moderne',
            startDate: '2024-03-01',
            durationInMonths: 4,
            orderRenewalInMonths: 2,
            daysPerYear: 200,
            documents: ['Contrat freelance', 'NDA', 'Guide de style'],
            link: 'https://startup-innovante.com/dev-portal',
            personalRating: 5,
            notes: 'Projet très enrichissant avec beaucoup d\'autonomie. Équipe jeune et dynamique.',
            freelanceId: 1,
            sourceName: 'Recommandation',
            sourceId: 2,
            totalRevenue: 44000,
            totalSteps: 4,
            completedSteps: 4,
            failedSteps: 0
          },
          {
            id: 3,
            role: 'Architecte Solution',
            clientName: 'GrandGroupe',
            clientId: 3,
            dailyRate: 750,
            workMode: 'ONSITE',
            remoteDaysPerMonth: 0,
            onsiteDaysPerMonth: 20,
            techStack: 'Java, Spring Boot, Microservices',
            description: 'Architecture et mise en place d\'une solution microservices pour un système bancaire. Conception de l\'architecture globale et accompagnement des équipes de développement.',
            advantages: 'Projet d\'envergure, équipe expérimentée, secteur bancaire stable',
            startDate: '2024-06-01',
            durationInMonths: 12,
            orderRenewalInMonths: 6,
            daysPerYear: 230,
            documents: ['Contrat cadre', 'Habilitation bancaire', 'Architecture document'],
            link: 'https://grandgroupe.com/contractor-portal',
            personalRating: 3,
            notes: 'Projet complexe avec beaucoup de contraintes réglementaires. Environnement très structuré.',
            freelanceId: 1,
            sourceName: 'Cabinet de recrutement',
            sourceId: 3,
            totalRevenue: 180000,
            totalSteps: 6,
            completedSteps: 2,
            failedSteps: 1
          }
        ];

        const project = mockProjects.find(p => p.id === id);
        if (project) {
          observer.next(project);
        } else {
          observer.error(new Error('Project not found'));
        }
        observer.complete();
      }, 300);
    });
  }

  getByIdWithSteps(id: number): Observable<ProjectDto> {
    return this.http.get<ProjectDto>(`${this.API_URL}/${id}/with-steps`);
  }

  getByFreelanceId(freelanceId: number): Observable<ProjectDto[]> {
    // Mock data for development - replace with real API call when backend is ready
    return new Observable(observer => {
      setTimeout(() => {
        const mockProjects: ProjectDto[] = [
          {
            id: 1,
            role: 'Développeur Full Stack',
            clientName: 'TechCorp',
            dailyRate: 600,
            workMode: 'HYBRID',
            techStack: 'React, Node.js, PostgreSQL',
            description: 'Développement d\'une application web moderne pour la gestion des ressources humaines.',
            startDate: '2024-01-15',
            durationInMonths: 6,
            daysPerYear: 220
          },
          {
            id: 2,
            role: 'Consultant Angular',
            clientName: 'StartupInnovante',
            dailyRate: 550,
            workMode: 'REMOTE',
            techStack: 'Angular, TypeScript, Firebase',
            description: 'Refonte complète de l\'interface utilisateur d\'une plateforme e-commerce.',
            startDate: '2024-03-01',
            durationInMonths: 4,
            daysPerYear: 200
          },
          {
            id: 3,
            role: 'Architecte Solution',
            clientName: 'GrandGroupe',
            dailyRate: 750,
            workMode: 'ONSITE',
            techStack: 'Java, Spring Boot, Microservices',
            description: 'Architecture et mise en place d\'une solution microservices pour un système bancaire.',
            startDate: '2024-06-01',
            durationInMonths: 12,
            daysPerYear: 230
          }
        ];
        observer.next(mockProjects);
        observer.complete();
      }, 500);
    });
  }

  getByClientId(clientId: number): Observable<ProjectDto[]> {
    return this.http.get<ProjectDto[]>(`${this.API_URL}/by-client/${clientId}`);
  }

  getByFreelanceIdWithFilters(freelanceId: number, filters: ProjectFilters): Observable<ProjectDto[]> {
    let params = new HttpParams();
    
    if (filters.minRate !== undefined) {
      params = params.set('minRate', filters.minRate.toString());
    }
    if (filters.maxRate !== undefined) {
      params = params.set('maxRate', filters.maxRate.toString());
    }
    if (filters.workMode) {
      params = params.set('workMode', filters.workMode);
    }
    if (filters.startDateAfter) {
      params = params.set('startDateAfter', filters.startDateAfter);
    }
    if (filters.techStack) {
      params = params.set('techStack', filters.techStack);
    }

    return this.http.get<ProjectDto[]>(`${this.API_URL}/by-freelance/${freelanceId}/filtered`, { params });
  }

  create(project: ProjectDto): Observable<ProjectDto> {
    return this.http.post<ProjectDto>(this.API_URL, project);
  }

  update(id: number, project: ProjectDto): Observable<ProjectDto> {
    return this.http.put<ProjectDto>(`${this.API_URL}/${id}`, project);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  getAverageDailyRate(freelanceId: number): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/stats/average-rate/${freelanceId}`);
  }

  getProjectCount(freelanceId: number): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/stats/count/${freelanceId}`);
  }
}
