package dev.swirlit.indezy.service;

import org.springframework.stereotype.Component;

/** The services an import creates records through, so they keep the rules of the API. */
@Component
record ImportWriters(SourceService sources, ClientService clients, ContactService contacts, SeasonService seasons,
                     ProjectService projects, InterviewStepService steps, ProjectNoteService notes) {
}
