// Interview step related interfaces and types

export enum StepStatus {
  TO_PLAN = 'TO_PLAN',
  PLANNED = 'PLANNED',
  CANCELED = 'CANCELED',
  WAITING_FEEDBACK = 'WAITING_FEEDBACK',
  VALIDATED = 'VALIDATED',
  FAILED = 'FAILED'
}

export interface InterviewStepDto {
  id?: number;
  title: string;
  date?: string;
  status: StepStatus;
  notes?: string;
  projectId?: number;
  projectRole?: string;
}

export interface CreateInterviewStepDto {
  title: string;
  date?: string;
  status: StepStatus;
  notes?: string;
  projectId: number;
}

export interface UpdateInterviewStepDto {
  id: number;
  title?: string;
  date?: string;
  status?: StepStatus;
  notes?: string;
}

export interface KanbanBoardDto {
  columns: { [stepTitle: string]: ProjectCardDto[] };
  stepOrder: string[];
}

export interface ProjectCardDto {
  projectId: number;
  role: string;
  clientName?: string;
  dailyRate: number;
  workMode?: string;
  techStack?: string;
  currentStepTitle: string;
  currentStepStatus: string;
  currentStepDate?: string;
  notes?: string;
  totalSteps: number;
  completedSteps: number;
  failedSteps: number;
}

export interface StepTransitionDto {
  projectId: number;
  fromStepTitle: string;
  toStepTitle: string;
  notes?: string;
}

// Constants for interview steps
export const INTERVIEW_STEPS_ORDER: string[] = [
  'Prise de Contact',
  'Entretien Commercial',
  'Positionnement',
  'Test Technique',
  'Entretien Technique',
  'Entretien Manager',
  'Validation'
];

export type InterviewStepTitle = string;

// Status display labels
export const STEP_STATUS_LABELS: { [key in StepStatus]: string } = {
  [StepStatus.TO_PLAN]: 'À planifier',
  [StepStatus.PLANNED]: 'Planifié',
  [StepStatus.CANCELED]: 'Annulé',
  [StepStatus.WAITING_FEEDBACK]: 'En attente de retour',
  [StepStatus.VALIDATED]: 'Validé',
  [StepStatus.FAILED]: 'Échoué'
};

// Status colors for UI
export const STEP_STATUS_COLORS: { [key in StepStatus]: string } = {
  [StepStatus.TO_PLAN]: '#ffa726', // orange
  [StepStatus.PLANNED]: '#42a5f5', // blue
  [StepStatus.CANCELED]: '#bdbdbd', // grey
  [StepStatus.WAITING_FEEDBACK]: '#ffee58', // yellow
  [StepStatus.VALIDATED]: '#66bb6a', // green
  [StepStatus.FAILED]: '#ef5350' // red
};
