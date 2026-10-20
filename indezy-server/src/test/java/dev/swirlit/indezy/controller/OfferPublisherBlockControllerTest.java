package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.OfferPublisherBlockDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.service.OfferPublisherBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the workspace's blocklist. */
@SpringBootTest
@AutoConfigureWebMvc
@WithMockUser
@ActiveProfiles("test")
class OfferPublisherBlockControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private OfferPublisherBlockService blocks;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void listsBlocksAndUnblocksPublishers() throws Exception {
        when(blocks.list(3L)).thenReturn(List.of(new OfferPublisherBlockDto(8L, "Exemple Conseil")));
        when(blocks.block(3L, "Acme")).thenReturn(new OfferPublisherBlockDto(9L, "Acme"));
        doThrow(new ResourceNotFoundException("Blocked publisher not found with id: 10")).when(blocks).unblock(3L, 10L);

        mockMvc.perform(get("/offers/blocked-publishers").param("freelanceId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Exemple Conseil"));
        mockMvc.perform(post("/offers/blocked-publishers").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(9));
        mockMvc.perform(delete("/offers/blocked-publishers/8").param("freelanceId", "3")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/offers/blocked-publishers/10").param("freelanceId", "3")).andExpect(status().isNotFound());
        verify(blocks).unblock(3L, 8L);
    }

    @Test
    void refusesAMissingNameOrWorkspace() throws Exception {
        mockMvc.perform(post("/offers/blocked-publishers").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/offers/blocked-publishers").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "x".repeat(201) + "\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/offers/blocked-publishers"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("This action needs a workspace"));
        verify(blocks, never()).block(any(), any());
    }
}
