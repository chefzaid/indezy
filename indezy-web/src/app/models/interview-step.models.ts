// Interview step related interfaces and types

export enum StepStatus {
  TO_PLAN = 'TO_PLAN',
  PLANNED = 'PLANNED',
  CANCELED = 'CANCELED',
  WAITING_FEEDBACK = 'WAITING_FEEDBACK',
  VALIDATED = 'VALIDATED',
  FAILED = 'FAILED'
}

/** One thing to prepare before a step, ticked once done. */
export interface StepChecklistItem {
  label: string;
  done: boolean;
}

export interface InterviewStepDto {
  id?: number;
  title: string;
  date?: string;
  status: StepStatus;
  notes?: string;
  /** The preparation checklist, in its order; a step saved without one has none. */
  checklist?: StepChecklistItem[];
  projectId?: number;
  projectRole?: string;
}

export interface CreateInterviewStepDto {
  title: string;
  date?: string;
  status: StepStatus;
  notes?: string;
  checklist?: StepChecklistItem[];
  projectId: number;
}

export interface UpdateInterviewStepDto {
  id: number;
  title?: string;
  date?: string;
  status?: StepStatus;
  notes?: string;
  checklist?: StepChecklistItem[];
  projectId?: number;
}

// Status colors for UI, dark enough to carry white text in status pills (4.5:1 contrast at least)
export const STEP_STATUS_COLORS: { [key in StepStatus]: string } = {
  [StepStatus.TO_PLAN]: '#c2410c', // orange
  [StepStatus.PLANNED]: '#2563eb', // blue
  [StepStatus.CANCELED]: '#64748b', // slate
  [StepStatus.WAITING_FEEDBACK]: '#a16207', // amber
  [StepStatus.VALIDATED]: '#15803d', // green
  [StepStatus.FAILED]: '#b91c1c' // red
};
