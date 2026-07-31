/**
 * Predefined note scaffolds a freelancer can insert when logging a project note
 * (call debrief, interview debrief, negotiation summary). Labels and bodies are
 * resolved from the translation bundle so they stay localized.
 */
export interface NoteTemplate {
  id: string;
  labelKey: string;
  bodyKey: string;
}

export const NOTE_TEMPLATES: NoteTemplate[] = [
  {
    id: 'callDebrief',
    labelKey: 'projects.notes.templates.callDebrief.label',
    bodyKey: 'projects.notes.templates.callDebrief.body'
  },
  {
    id: 'interviewDebrief',
    labelKey: 'projects.notes.templates.interviewDebrief.label',
    bodyKey: 'projects.notes.templates.interviewDebrief.body'
  },
  {
    id: 'negotiationSummary',
    labelKey: 'projects.notes.templates.negotiationSummary.label',
    bodyKey: 'projects.notes.templates.negotiationSummary.body'
  }
];
