package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.OfferBoardVisitDto;
import dev.swirlit.indezy.dto.OfferSiteExclusionDto;
import dev.swirlit.indezy.dto.OfferSiteHealthDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.OfferSiteRefresher;
import dev.swirlit.indezy.service.OfferSiteHealthService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the site health page. */
@SpringBootTest
@AutoConfigureWebMvc
@WithMockUser
@ActiveProfiles("test")
class OfferSiteControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private OfferSiteHealthService healthService;

    @MockitoBean
    private OfferSiteRefresher refresher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void describesEachSiteAndTheOnesLeftOut() throws Exception {
        Instant now = Instant.parse("2026-10-19T10:00:00Z");
        when(healthService.health(3L)).thenReturn(new OfferSiteHealthDto(List.of(new OfferSiteHealthDto.Site(OfferSite.YEETS, "Yeets",
                "https://www.yeets.fr", OfferSiteHealthDto.Status.FAILING, now, null, now.plusSeconds(3600), "SiteResponseException: 503", 2,
                null, null, 0, 3, 12, now.minusSeconds(86_400), true, now.plusSeconds(600))),
            List.of(new OfferSiteHealthDto.LeftOut(OfferLeftOutSite.MALT, "Malt", "https://www.malt.fr", OfferLeftOutSite.Kind.MATCHING,
                OfferLeftOutSite.Access.PROFILE, now.minusSeconds(3600))), true));

        mockMvc.perform(get("/offers/sites/health").param("freelanceId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refreshEnabled").value(true))
            .andExpect(jsonPath("$.sites[0].status").value("FAILING"))
            .andExpect(jsonPath("$.sites[0].lastError").value("SiteResponseException: 503"))
            .andExpect(jsonPath("$.sites[0].collectedWeek").value(3))
            .andExpect(jsonPath("$.sites[0].excluded").value(true))
            .andExpect(jsonPath("$.sites[0].refreshAvailableAt").value("2026-10-19T10:10:00Z"))
            .andExpect(jsonPath("$.leftOut[0].site").value("MALT"))
            .andExpect(jsonPath("$.leftOut[0].kind").value("MATCHING"))
            .andExpect(jsonPath("$.leftOut[0].visitedAt").value("2026-10-19T09:00:00Z"));
    }

    @Test
    void excludesASiteForTheWorkspace() throws Exception {
        when(healthService.setExcluded(3L, OfferSite.YEETS, true)).thenReturn(new OfferSiteExclusionDto(OfferSite.YEETS, true));

        mockMvc.perform(put("/offers/sites/YEETS/exclusion").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"excluded\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.excluded").value(true));
        mockMvc.perform(put("/offers/sites/YEETS/exclusion").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/offers/sites/LINKEDIN/exclusion").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"excluded\":true}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/offers/sites/YEETS/exclusion").contentType(MediaType.APPLICATION_JSON).content("{\"excluded\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("This action needs a workspace"));
    }

    @Test
    void recordsAndForgetsTheWorkspacesVisitOfASite() throws Exception {
        when(healthService.visit(3L, OfferLeftOutSite.OPTEAMIS))
            .thenReturn(new OfferBoardVisitDto(OfferLeftOutSite.OPTEAMIS, Instant.parse("2026-10-20T10:00:00Z")));
        when(healthService.clearVisit(3L, OfferLeftOutSite.OPTEAMIS)).thenReturn(new OfferBoardVisitDto(OfferLeftOutSite.OPTEAMIS, null));
        when(healthService.visit(3L, OfferLeftOutSite.LINKEDIN)).thenThrow(new IllegalArgumentException("LinkedIn is not one of the sites to visit"));

        mockMvc.perform(put("/offers/sites/left-out/OPTEAMIS/visit").param("freelanceId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visitedAt").value("2026-10-20T10:00:00Z"));
        mockMvc.perform(delete("/offers/sites/left-out/OPTEAMIS/visit").param("freelanceId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visitedAt").doesNotExist());
        mockMvc.perform(put("/offers/sites/left-out/LINKEDIN/visit").param("freelanceId", "3")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/offers/sites/left-out/YEETS/visit").param("freelanceId", "3")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/offers/sites/left-out/OPTEAMIS/visit")).andExpect(status().isBadRequest());
    }

    @Test
    void readsASiteOnRequestOrSaysWhenItCanBe() throws Exception {
        when(refresher.refresh(OfferSite.YEETS)).thenReturn(Instant.parse("2026-10-19T10:15:00Z"));
        when(refresher.refresh(OfferSite.LEHIBOU)).thenThrow(new TooManyRequestsException(
            "LeHibou is read on request at most once every 15 minutes, and not while a reading runs", Duration.ofSeconds(540)));
        when(refresher.refresh(OfferSite.KOINO)).thenThrow(new InvalidStateException("Offer collection is off on this server"));

        mockMvc.perform(post("/offers/sites/YEETS/refresh").param("freelanceId", "3"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.refreshAvailableAt").value("2026-10-19T10:15:00Z"));
        mockMvc.perform(post("/offers/sites/LEHIBOU/refresh").param("freelanceId", "3"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "540"))
            .andExpect(jsonPath("$.message").value("LeHibou is read on request at most once every 15 minutes, and not while a reading runs"));
        mockMvc.perform(post("/offers/sites/KOINO/refresh").param("freelanceId", "3")).andExpect(status().isConflict());
        mockMvc.perform(post("/offers/sites/FREE_WORK/refresh")).andExpect(status().isBadRequest());
        verify(refresher, never()).refresh(OfferSite.FREE_WORK);
    }
}
