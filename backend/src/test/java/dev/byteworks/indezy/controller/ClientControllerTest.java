package dev.byteworks.indezy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.byteworks.indezy.dto.ClientDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.service.ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@WithMockUser
@ActiveProfiles("test")
class ClientControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ClientService clientService;

    @Autowired
    private ObjectMapper objectMapper;

    private ClientDto testClientDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        testClientDto = new ClientDto();
        testClientDto.setId(1L);
        testClientDto.setCompanyName("Test Company");
        testClientDto.setAddress("123 Test St");
        testClientDto.setCity("Test City");
        testClientDto.setDomain("Technology");
        testClientDto.setIsFinal(true);
        testClientDto.setNotes("Test notes");
        testClientDto.setFreelanceId(1L);
    }

    @Test
    @WithMockUser
    void getAllClients_ShouldReturnClientList() throws Exception {
        // Given
        List<ClientDto> clients = Arrays.asList(testClientDto);
        when(clientService.findAll()).thenReturn(clients);

        // When & Then
        mockMvc.perform(get("/clients"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].companyName", is("Test Company")));

        verify(clientService).findAll();
    }

    @Test
    @WithMockUser
    void getClientById_WithExistingId_ShouldReturnClient() throws Exception {
        // Given
        when(clientService.findById(1L)).thenReturn(testClientDto);

        // When & Then
        mockMvc.perform(get("/clients/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.companyName", is("Test Company")));

        verify(clientService).findById(1L);
    }

    @Test
    @WithMockUser
    void getClientById_WithNonExistentId_ShouldReturnNotFound() throws Exception {
        // Given
        when(clientService.findById(999L)).thenThrow(new ResourceNotFoundException("Client not found with id: 999"));

        // When & Then
        mockMvc.perform(get("/clients/999"))
                .andExpect(status().isNotFound());

        verify(clientService).findById(999L);
    }

    @Test
    @WithMockUser
    void createClient_WithValidData_ShouldCreateAndReturnClient() throws Exception {
        // Given
        when(clientService.create(any(ClientDto.class))).thenReturn(testClientDto);

        // When & Then
        mockMvc.perform(post("/clients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testClientDto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.companyName", is("Test Company")));

        verify(clientService).create(any(ClientDto.class));
    }

    @Test
    @WithMockUser
    void createClient_WithInvalidData_ShouldReturnValidationError() throws Exception {
        // Given
        ClientDto invalidClient = new ClientDto();
        // Missing required fields

        // When & Then
        mockMvc.perform(post("/clients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidClient)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @WithMockUser
    void updateClient_WithValidData_ShouldUpdateAndReturnClient() throws Exception {
        // Given
        testClientDto.setCompanyName("Updated Company");
        when(clientService.update(eq(1L), any(ClientDto.class))).thenReturn(testClientDto);

        // When & Then
        mockMvc.perform(put("/clients/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testClientDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.companyName", is("Updated Company")));

        verify(clientService).update(eq(1L), any(ClientDto.class));
    }

    @Test
    @WithMockUser
    void deleteClient_WithExistingId_ShouldDeleteClient() throws Exception {
        // Given
        doNothing().when(clientService).delete(1L);

        // When & Then
        mockMvc.perform(delete("/clients/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(clientService).delete(1L);
    }

    @Test
    @WithMockUser
    void getClientsByFreelanceId_ShouldReturnClientList() throws Exception {
        // Given
        List<ClientDto> clients = Arrays.asList(testClientDto);
        when(clientService.findByFreelanceId(1L)).thenReturn(clients);

        // When & Then
        mockMvc.perform(get("/clients/by-freelance/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].companyName", is("Test Company")));

        verify(clientService).findByFreelanceId(1L);
    }

    @Test
    @WithMockUser
    void getClientWithProjects_ShouldReturnClientWithProjects() throws Exception {
        // Given
        when(clientService.findByIdWithProjects(1L)).thenReturn(testClientDto);

        // When & Then
        mockMvc.perform(get("/clients/1/with-projects"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.companyName", is("Test Company")));

        verify(clientService).findByIdWithProjects(1L);
    }

    @Test
    @WithMockUser
    void getClientWithContacts_ShouldReturnClientWithContacts() throws Exception {
        // Given
        when(clientService.findByIdWithContacts(1L)).thenReturn(testClientDto);

        // When & Then
        mockMvc.perform(get("/clients/1/with-contacts"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.companyName", is("Test Company")));

        verify(clientService).findByIdWithContacts(1L);
    }
}
