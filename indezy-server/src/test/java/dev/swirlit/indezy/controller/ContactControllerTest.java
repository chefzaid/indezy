package dev.swirlit.indezy.controller;

import tools.jackson.databind.ObjectMapper;
import dev.swirlit.indezy.dto.ContactDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    void getAllContacts_ShouldReturnContactList() throws Exception {
        when(contactService.findAll()).thenReturn(List.of(testContactDto));

        mockMvc.perform(get("/contacts"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firstName", is("Jane")));

        verify(contactService).findAll();
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

    @Test
    void searchContactsByName_ShouldReturnMatchingContacts() throws Exception {
        when(contactService.searchByName(1L, "Jane")).thenReturn(List.of(testContactDto));

        mockMvc.perform(get("/contacts/by-freelance/1/search/name").param("name", "Jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(contactService).searchByName(1L, "Jane");
    }

    @Test
    void searchContactsByEmail_ShouldReturnMatchingContacts() throws Exception {
        when(contactService.searchByEmail(1L, "jane")).thenReturn(List.of(testContactDto));

        mockMvc.perform(get("/contacts/by-freelance/1/search/email").param("email", "jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(contactService).searchByEmail(1L, "jane");
    }
}
