package dev.swirlit.indezy.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SkillControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SkillController()).build();

    @Test
    void listsTheKnownSkillsWithTheirAliases() throws Exception {
        mockMvc.perform(get("/skills/aliases"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.JavaScript", hasItems("JS", "ECMAScript")))
            .andExpect(jsonPath("$.Kubernetes", hasItems("K8s")));
    }
}
