package dev.swirlit.indezy.config;

import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.RefusedBy;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** The opportunities refused before Indezy asked who refused them say it when their reason tells. */
@DataJpaTest
@ActiveProfiles("test")
class RefusedByBackfillTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void tellsWhoRefusedFromTheReasonWithoutChangingASideAlreadySaid() {
        Freelance freelance = new Freelance();
        freelance.setFirstName("Ada");
        freelance.setLastName("Lovelace");
        freelance.setEmail("ada@example.com");
        freelance.setStatus(EmploymentStatus.FREELANCE);
        freelance = entityManager.persist(freelance);
        Client client = new Client();
        client.setCompanyName("Acme");
        client.setCity("Paris");
        client.setIsFinal(true);
        client.setFreelance(freelance);
        client = entityManager.persist(client);
        long otherOffer = refused(freelance, client, ProjectStatus.LOST, null, LostReason.ACCEPTED_OTHER_OFFER);
        long filled = refused(freelance, client, ProjectStatus.LOST, null, LostReason.POSITION_FILLED);
        long said = refused(freelance, client, ProjectStatus.LOST, RefusedBy.FREELANCE, LostReason.NO_RESPONSE);
        long other = refused(freelance, client, ProjectStatus.LOST, null, LostReason.OTHER);
        long none = refused(freelance, client, ProjectStatus.LOST, null, null);
        long open = refused(freelance, client, ProjectStatus.INTERVIEW, null, LostReason.NO_RESPONSE);
        entityManager.flush();

        RefusedByBackfill backfill = new RefusedByBackfill(projectRepository);
        backfill.run(null);
        backfill.run(null);

        assertThat(refusedBy(otherOffer)).isEqualTo(RefusedBy.FREELANCE);
        assertThat(refusedBy(filled)).isEqualTo(RefusedBy.RECRUITER);
        assertThat(refusedBy(said)).isEqualTo(RefusedBy.FREELANCE);
        assertThat(refusedBy(other)).isNull();
        assertThat(refusedBy(none)).isNull();
        assertThat(refusedBy(open)).isNull();
    }

    private long refused(Freelance freelance, Client client, ProjectStatus status, RefusedBy refusedBy, LostReason reason) {
        Project project = new Project();
        project.setRole("Java developer");
        project.setDailyRate(600);
        project.setFreelance(freelance);
        project.setClient(client);
        project.setStatus(status);
        project.setRefusedBy(refusedBy);
        project.setLostReason(reason);
        return entityManager.persist(project).getId();
    }

    private RefusedBy refusedBy(long id) {
        return projectRepository.findById(id).orElseThrow().getRefusedBy();
    }
}
