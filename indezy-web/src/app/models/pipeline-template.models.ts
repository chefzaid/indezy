/** One step of a saved recruitment process, with the preparation items it starts with. */
export interface PipelineTemplateStep {
  title: string;
  checklist: string[];
}

/** A recruitment process saved from an opportunity's steps, for a client or ESN or for anyone. */
export interface PipelineTemplate {
  id: number;
  name: string;
  clientId?: number;
  clientName?: string;
  steps: PipelineTemplateStep[];
}
