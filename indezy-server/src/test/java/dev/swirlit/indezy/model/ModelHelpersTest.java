package dev.swirlit.indezy.model;

import dev.swirlit.indezy.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelHelpersTest {

    @Nested
    class ContactTest {

        @Test
        void getFullName_WithFirstAndLastName_ShouldConcatenate() {
            Contact contact = new Contact();
            contact.setFirstName("Jane");
            contact.setLastName("Smith");

            assertThat(contact.getFullName()).isEqualTo("Jane Smith");
        }

        @Test
        void getFullName_WithoutLastName_ShouldReturnFirstNameOnly() {
            Contact contact = new Contact();
            contact.setFirstName("Jane");
            contact.setLastName(null);

            assertThat(contact.getFullName()).isEqualTo("Jane");
        }

        @Test
        void getFullName_WithBlankLastName_ShouldReturnFirstNameOnly() {
            Contact contact = new Contact();
            contact.setFirstName("Jane");
            contact.setLastName("   ");

            assertThat(contact.getFullName()).isEqualTo("Jane");
        }

        @Test
        void settersAndGetters_ShouldRoundTrip() {
            Contact contact = new Contact();
            Client client = new Client();
            Freelance freelance = new Freelance();
            contact.setEmail("jane@example.com");
            contact.setPhone("0123456789");
            contact.setNotes("notes");
            contact.setClient(client);
            contact.setFreelance(freelance);

            assertThat(contact.getEmail()).isEqualTo("jane@example.com");
            assertThat(contact.getPhone()).isEqualTo("0123456789");
            assertThat(contact.getNotes()).isEqualTo("notes");
            assertThat(contact.getClient()).isSameAs(client);
            assertThat(contact.getFreelance()).isSameAs(freelance);
        }
    }

    @Nested
    class UserSessionTest {

        @Test
        void isActive_WithRecentActivity_ShouldReturnTrue() {
            UserSession session = new UserSession();
            session.setLastActive(LocalDateTime.now().minusHours(1));

            assertThat(session.isActive()).isTrue();
        }

        @Test
        void isActive_WithOldActivity_ShouldReturnFalse() {
            UserSession session = new UserSession();
            session.setLastActive(LocalDateTime.now().minusDays(2));

            assertThat(session.isActive()).isFalse();
        }

        @Test
        void isActive_WithoutActivity_ShouldReturnFalse() {
            UserSession session = new UserSession();
            session.setLastActive(null);

            assertThat(session.isActive()).isFalse();
        }

        @Test
        void settersAndGetters_ShouldRoundTrip() {
            UserSession session = new UserSession();
            session.setSessionId("abc");
            session.setDevice("Desktop");
            session.setBrowser("Firefox");
            session.setLocation("Paris");
            session.setIpAddress("127.0.0.1");
            session.setIsCurrent(true);

            assertThat(session.getSessionId()).isEqualTo("abc");
            assertThat(session.getDevice()).isEqualTo("Desktop");
            assertThat(session.getBrowser()).isEqualTo("Firefox");
            assertThat(session.getLocation()).isEqualTo("Paris");
            assertThat(session.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(session.getIsCurrent()).isTrue();
        }
    }

    @Nested
    class ClientCollectionsTest {

        @Test
        void collectionAccessors_ShouldReturnDefensiveCopies() {
            Client client = new Client();
            Project project = new Project();
            Contact contact = new Contact();

            client.setProjects(List.of(project));
            client.setMiddlemanProjects(List.of(project));
            client.setContacts(List.of(contact));

            assertThat(client.getProjects()).containsExactly(project);
            assertThat(client.getMiddlemanProjects()).containsExactly(project);
            assertThat(client.getContacts()).containsExactly(contact);
            // Mutating the returned list must not affect the entity state.
            client.getProjects().clear();
            assertThat(client.getProjects()).hasSize(1);
        }

        @Test
        void collectionSetters_WithNull_ShouldResetToEmptyLists() {
            Client client = new Client();
            client.setProjects(null);
            client.setMiddlemanProjects(null);
            client.setContacts(null);

            assertThat(client.getProjects()).isEmpty();
            assertThat(client.getMiddlemanProjects()).isEmpty();
            assertThat(client.getContacts()).isEmpty();
        }
    }

    @Nested
    class FreelanceTest {

        @Test
        void getFullName_ShouldConcatenateNames() {
            Freelance freelance = new Freelance();
            freelance.setFirstName("John");
            freelance.setLastName("Doe");

            assertThat(freelance.getFullName()).isEqualTo("John Doe");
        }

        @Test
        void collectionAccessors_ShouldReturnDefensiveCopies() {
            Freelance freelance = new Freelance();
            freelance.setProjects(List.of(new Project()));
            freelance.setClients(List.of(new Client()));
            freelance.setContacts(List.of(new Contact()));
            freelance.setSources(List.of(new Source()));

            assertThat(freelance.getProjects()).hasSize(1);
            assertThat(freelance.getClients()).hasSize(1);
            assertThat(freelance.getContacts()).hasSize(1);
            assertThat(freelance.getSources()).hasSize(1);
            freelance.getProjects().clear();
            assertThat(freelance.getProjects()).hasSize(1);
        }

        @Test
        void collectionSetters_WithNull_ShouldResetToEmptyLists() {
            Freelance freelance = new Freelance();
            freelance.setProjects(null);
            freelance.setClients(null);
            freelance.setContacts(null);
            freelance.setSources(null);

            assertThat(freelance.getProjects()).isEmpty();
            assertThat(freelance.getClients()).isEmpty();
            assertThat(freelance.getContacts()).isEmpty();
            assertThat(freelance.getSources()).isEmpty();
        }
    }

    @Nested
    class ResourceNotFoundExceptionTest {

        @Test
        void constructor_WithCause_ShouldPreserveMessageAndCause() {
            Throwable cause = new IllegalStateException("root");
            ResourceNotFoundException ex = new ResourceNotFoundException("not found", cause);

            assertThat(ex.getMessage()).isEqualTo("not found");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }
}
