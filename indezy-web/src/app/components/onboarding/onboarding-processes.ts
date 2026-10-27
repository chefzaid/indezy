import { PipelineTemplateStep } from '../../models';

/** A starter recruitment process offered to a new account: its template name and steps, as translation keys. */
export interface StarterProcess {
  id: 'ESN' | 'DIRECT';
  nameKey: string;
  hintKey: string;
  steps: { titleKey: string; checklistKeys: string[] }[];
}

const STEP = 'onboarding.process.steps.';
const ITEM = 'onboarding.process.checklist.';

export const STARTER_PROCESSES: StarterProcess[] = [
  {
    id: 'ESN',
    nameKey: 'onboarding.process.esn',
    hintKey: 'onboarding.process.esnHint',
    steps: [
      { titleKey: `${STEP}esnCall`, checklistKeys: [`${ITEM}rate`, `${ITEM}availability`] },
      { titleKey: `${STEP}technical`, checklistKeys: [`${ITEM}stack`] },
      { titleKey: `${STEP}clientInterview`, checklistKeys: [`${ITEM}stack`, `${ITEM}references`] },
      { titleKey: `${STEP}offer`, checklistKeys: [`${ITEM}terms`] }
    ]
  },
  {
    id: 'DIRECT',
    nameKey: 'onboarding.process.direct',
    hintKey: 'onboarding.process.directHint',
    steps: [
      { titleKey: `${STEP}discovery`, checklistKeys: [`${ITEM}rate`, `${ITEM}availability`] },
      { titleKey: `${STEP}clientInterview`, checklistKeys: [`${ITEM}stack`, `${ITEM}references`] },
      { titleKey: `${STEP}proposal`, checklistKeys: [`${ITEM}terms`] }
    ]
  }
];

/** The process's steps in the interface language, as a template stores them. */
export function starterSteps(process: StarterProcess, translate: (key: string) => string): PipelineTemplateStep[] {
  return process.steps.map(step => ({ title: translate(step.titleKey), checklist: step.checklistKeys.map(translate) }));
}
