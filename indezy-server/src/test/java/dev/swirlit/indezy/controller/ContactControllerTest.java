package dev.swirlit.indezy.controller;

import tools.jackson.databind.ObjectMapper;
import dev.swirlit.indezy.dto.ContactDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureWebMvc
@WithMockUser
@ActiveProfiles("test")
class ContactControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ContactService contactService;

    @Autowired
    private ObjectMapper objectMapper;

    private ContactDto testContactDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        testContactDto = new ContactDto();
        testContactDto.setId(1L);
        testContactDto.setFirstName("Jane");
        testContactDto.setLastName("Smith");
        testContactDto.setEmail("jane.smith@example.com");
        testContactDto.setPhone("0123456789");
        testContactDto.setFreelanceId(1L);
        testContactDto.setClientId(1L);
    }

    @Test
    void getContactById_WithExistingId_ShouldReturnContact() throws Exception {
        when(contactService.findById(1L)).thenReturn(testContactDto);

        mockMvc.perform(get("/contacts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Jane")))
                .andExpect(jsonPath("$.email", is("jane.smith@example.com")));

        verify(contactService).findById(1L);
    }

    @Test
    void getContactById_WithNonExistingId_ShouldReturnNotFound() throws Exception {
        when(contactService.findById(999L)).thenThrow(new ResourceNotFoundException("Contact not found with id: 999"));

        mockMvc.perform(get("/contacts/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createContact_WithValidData_ShouldReturnCreated() throws Exception {
        when(contactService.create(any(ContactDto.class))).thenReturn(testContactDto);

        mockMvc.perform(post("/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testContactDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName", is("Jane")));

        verify(contactService).create(any(ContactDto.class));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void createContact_WithInvalidEmail_ShouldLogTheFieldWithoutTheRejectedValue(CapturedOutput output) throws Exception {
        testContactDto.setEmail("jane.private(at)mail.example");

        mockMvc.perform(post("/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testContactDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email", is("Email should be valid")));

        // The test profile logs Spring MVC at DEBUG, so only the handler's own lines are checked.
        List<String> handlerLines = output.getAll().lines().filter(line -> line.contains("GlobalExceptionHandler")).toList();
        assertThat(handlerLines).anyMatch(line -> line.contains("WARN") && line.contains("Validation failed for contactDto: email (Email)"));
        assertThat(handlerLines).noneMatch(line -> line.contains("jane.private"));
        verify(contactService, never()).create(any(ContactDto.class));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void createContact_WithTextPlainBody_ShouldAnswerUnsupportedMediaTypeAndLogAtWarn(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/contacts")
                        .with(csrf())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Jane Smith"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.ACCEPT, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.status", is(415)))
                .andExpect(jsonPath("$.error", is("Unsupported Media Type")))
                .andExpect(jsonPath("$.message", is("Content-Type 'text/plain' is not supported.")));

        List<String> handlerLines = output.getAll().lines().filter(line -> line.contains("GlobalExceptionHandler")).toList();
        assertThat(handlerLines).anyMatch(line -> line.contains("WARN") && line.endsWith("Rejected request: HttpMediaTypeNotSupportedException (415)"));
        assertThat(handlerLines).noneMatch(line -> line.contains("ERROR") || line.contains("text/plain"));
        verify(contactService, never()).create(any(ContactDto.class));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void getContactsByFreelance_AcceptingOnlyCsv_ShouldAnswerNotAcceptableWithoutABody(CapturedOutput output) throws Exception {
        when(contactService.findByFreelanceId(1L)).thenReturn(List.of(testContactDto));

        mockMvc.perform(get("/contacts/by-freelance/1").accept("text/csv"))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string(HttpHeaders.ACCEPT, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(content().string(""));

        assertThat(output.getAll().lines()).anyMatch(line -> line.contains("WARN") &&
            line.endsWith("Rejected request: HttpMediaTypeNotAcceptableException (406)"));
        // A JSON error body could not be written either, which made Spring log a failed handler and answer itself.
        assertThat(output.getAll()).doesNotContain("Failure in @ExceptionHandler").doesNotContain("Unexpected error");
    }

    @Test
    void updateContact_WithValidData_ShouldReturnUpdatedContact() throws Exception {
        when(contactService.update(eq(1L), any(ContactDto.class))).thenReturn(testContactDto);

        mockMvc.perform(put("/contacts/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testContactDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Jane")));

        verify(contactService).update(eq(1L), any(ContactDto.class));
    }

    @Test
    void updateContact_WithNonExistingId_ShouldReturnNotFound() throws Exception {
        when(contactService.update(eq(999L), any(ContactDto.class)))
                .thenThrow(new ResourceNotFoundException("Contact not found with id: 999"));

        mockMvc.perform(put("/contacts/999")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testContactDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteContact_WithExistingId_ShouldReturnNoContent() throws Exception {
        doNothing().when(contactService).delete(1L);

        mockMvc.perform(delete("/contacts/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(contactService).delete(1L);
    }

    @Test
    void deleteContact_WithNonExistingId_ShouldReturnNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Contact not found with id: 999"))
                .when(contactService).delete(999L);

        mockMvc.perform(delete("/contacts/999").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getContactsByFreelanceId_ShouldReturnContacts() throws Exception {
        when(contactService.findByFreelanceId(1L)).thenReturn(List.of(testContactDto));

        mockMvc.perform(get("/contacts/by-freelance/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(contactService).findByFreelanceId(1L);
    }

    @Test
    void getContactsByClientId_ShouldReturnContacts() throws Exception {
        when(contactService.findByClientId(1L)).thenReturn(List.of(testContactDto));

        mockMvc.perform(get("/contacts/by-client/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(contactService).findByClientId(1L);
    }

}
