package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.OfferApplicationRequest;
import dev.swirlit.indezy.dto.OfferBulkImportDto;
import dev.swirlit.indezy.dto.OfferBulkImportRequest;
import dev.swirlit.indezy.dto.OfferBulkStateRequest;
import dev.swirlit.indezy.dto.OfferCopyDto;
import dev.swirlit.indezy.dto.OfferDraftDto;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferLinkDto;
import dev.swirlit.indezy.dto.OfferMatchDto;
import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.dto.OfferMarketDto;
import dev.swirlit.indezy.dto.OfferPageDto;
import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.dto.OfferRegionDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferSiteDto;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.dto.OfferStateUpdateRequest;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.OfferApplication;
import dev.swirlit.indezy.model.enums.ApplicationChannel;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferLinkStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.OfferSuspicion;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.service.OfferApplicationService;
import dev.swirlit.indezy.service.OfferBulkService;
import dev.swirlit.indezy.service.OfferImportService;
import dev.swirlit.indezy.service.OfferLinkService;
import dev.swirlit.indezy.service.OfferMarketService;
import dev.swirlit.indezy.service.OfferSearchService;
import dev.swirlit.indezy.service.OfferStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the offer feed. */
@SpringBootTest
@AutoConfigureWebMvc
@WithMockUser
@ActiveProfiles("test")
class OfferControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private OfferSearchService searchService;

    @MockitoBean
    private OfferStateService stateService;

    @MockitoBean
    private OfferImportService importService;

    @MockitoBean
    private OfferMarketService marketService;

    @MockitoBean
    private OfferApplicationService applicationService;

    @MockitoBean
    private OfferBulkService bulkService;

    @MockitoBean
    private OfferLinkService linkService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void searchBindsEveryFilterFromTheQueryString() throws Exception {
        when(searchService.search(eq(3L), any())).thenReturn(new OfferPageDto(List.of(offer()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/offers").param("freelanceId", "3").param("q", "java spring").param("rateMin", "500")
                .param("rateKnown", "true").param("regions", "11", "84").param("departments", "2A")
                .param("workModes", "REMOTE,HYBRID").param("minRemoteDays", "2").param("durationMin", "3")
                .param("startBefore", "2026-12-01").param("publishedWithinDays", "7").param("sites", "FREE_WORK")
                .param("seniorities", "SENIOR").param("skills", "Java").param("view", "SAVED").param("unseenOnly", "true")
                .param("matchesProfile", "true").param("hideSuspicious", "true").param("sort", "RATE").param("page", "2").param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.distanceAvailable").value(true))
            .andExpect(jsonPath("$.matchAvailable").value(true))
            .andExpect(jsonPath("$.content[0].match.score").value(83))
            .andExpect(jsonPath("$.content[0].match.skills[0]").value("Java"))
            .andExpect(jsonPath("$.content[0].match.place").value("NEAR"))
            .andExpect(jsonPath("$.content[0].siteName").value("Free-Work"))
            .andExpect(jsonPath("$.content[0].publishedAt").value("2026-10-19T08:47:42Z"))
            .andExpect(jsonPath("$.content[0].description").doesNotExist())
            .andExpect(jsonPath("$.content[0].alsoOn[0].siteName").value("Collective"))
            .andExpect(jsonPath("$.content[0].alsoOn[0].url").value("https://www.collective.work/job/8"))
            .andExpect(jsonPath("$.content[0].note").value("Call Léa"))
            .andExpect(jsonPath("$.content[0].rateMarket.percent").value(9))
            .andExpect(jsonPath("$.content[0].rateMarket.regionName").value("Île-de-France"))
            .andExpect(jsonPath("$.content[0].suspicion[0]").value("SAME_TEXT_MANY_PUBLISHERS"))
            .andExpect(jsonPath("$.content[0].application.appliedOn").value("2026-10-20"))
            .andExpect(jsonPath("$.content[0].application.via").value("EMAIL"));

        ArgumentCaptor<OfferSearchRequest> request = ArgumentCaptor.forClass(OfferSearchRequest.class);
        verify(searchService).search(eq(3L), request.capture());
        OfferSearchRequest bound = request.getValue();
        assertThat(bound.getQ()).isEqualTo("java spring");
        assertThat(bound.getRegions()).containsExactly("11", "84");
        assertThat(bound.getDepartments()).containsExactly("2A");
        assertThat(bound.getWorkModes()).containsExactly(WorkMode.REMOTE, WorkMode.HYBRID);
        assertThat(bound.getStartBefore()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(bound.getSites()).containsExactly(OfferSite.FREE_WORK);
        assertThat(bound.getSeniorities()).containsExactly(Seniority.SENIOR);
        assertThat(bound.getView()).isEqualTo(OfferSearchRequest.View.SAVED);
        assertThat(bound.getSort()).isEqualTo(OfferSearchRequest.Sort.RATE);
        assertThat(bound.isRateKnown()).isTrue();
        assertThat(bound.isUnseenOnly()).isTrue();
        assertThat(bound.isMatchesProfile()).isTrue();
        assertThat(bound.isHideSuspicious()).isTrue();
        assertThat(bound.getPage()).isEqualTo(2);
        assertThat(bound.getSize()).isEqualTo(50);
    }

    @Test
    void searchRefusesInvalidFilters() throws Exception {
        mockMvc.perform(get("/offers").param("size", "500")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/offers").param("regions", "Paris")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/offers").param("sites", "LINKEDIN")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/offers").param("q", "x".repeat(201))).andExpect(status().isBadRequest());
        verify(searchService, never()).search(any(), any());
    }

    @Test
    void searchExplainsThatDistanceNeedsACity() throws Exception {
        when(searchService.search(any(), any())).thenThrow(new IllegalArgumentException("Add your city to your profile"));

        mockMvc.perform(get("/offers").param("radiusKm", "30"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Add your city to your profile"));
    }

    @Test
    void describesTheMarketOfTheFilteredOffers() throws Exception {
        when(marketService.snapshot(eq(3L), any())).thenReturn(new OfferMarketDto(12,
            new OfferMarketDto.Rates(10, 520, 600, 680, 600, 55), List.of(new OfferMarketDto.RateBin(null, 500, 2)),
            new OfferMarketDto.Remote(4, 6, 1, 1, 2.5), List.of(new OfferFacetDto("Java", 8)), List.of(new OfferFacetDto("Exemple Conseil", 3)),
            List.of(new OfferMarketDto.DayCount(LocalDate.of(2026, 10, 19), 5))));

        mockMvc.perform(get("/offers/market").param("freelanceId", "3").param("q", "java").param("workModes", "REMOTE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.offers").value(12))
            .andExpect(jsonPath("$.rates.median").value(600))
            .andExpect(jsonPath("$.rates.meetTarget").value(55))
            .andExpect(jsonPath("$.rateBins[0].from").doesNotExist())
            .andExpect(jsonPath("$.rateBins[0].to").value(500))
            .andExpect(jsonPath("$.remote.hybridRemoteDays").value(2.5))
            .andExpect(jsonPath("$.skills[0].value").value("Java"))
            .andExpect(jsonPath("$.intermediaries[0].offers").value(3))
            .andExpect(jsonPath("$.perDay[0].day").value("2026-10-19"));

        ArgumentCaptor<OfferSearchRequest> request = ArgumentCaptor.forClass(OfferSearchRequest.class);
        verify(marketService).snapshot(eq(3L), request.capture());
        assertThat(request.getValue().getQ()).isEqualTo("java");
        assertThat(request.getValue().getWorkModes()).containsExactly(WorkMode.REMOTE);
        mockMvc.perform(get("/offers/market").param("rateMin", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void getAnswersOneOfferOrNotFound() throws Exception {
        when(searchService.get(3L, 7L)).thenReturn(offer());
        when(searchService.get(3L, 8L)).thenThrow(new ResourceNotFoundException("Job offer not found with id: 8"));

        mockMvc.perform(get("/offers/7").param("freelanceId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Développeur Java"))
            .andExpect(jsonPath("$.skills[1]").value("Kafka"));
        mockMvc.perform(get("/offers/8").param("freelanceId", "3")).andExpect(status().isNotFound());
    }

    @Test
    void updatesTheWorkspacesState() throws Exception {
        when(stateService.update(3L, 7L, new OfferStateUpdateRequest(true, null, null)))
            .thenReturn(new OfferStateDto(7L, true, false, false, null));

        mockMvc.perform(put("/offers/7/state").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"saved\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saved").value(true));
        mockMvc.perform(put("/offers/7/state").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"" + "x".repeat(2001) + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void stateAndImportNeedAWorkspace() throws Exception {
        mockMvc.perform(put("/offers/7/state").contentType(MediaType.APPLICATION_JSON).content("{\"saved\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("This action needs a workspace"));
        mockMvc.perform(post("/offers/7/import")).andExpect(status().isBadRequest());
        verify(importService, never()).importOffer(any(), any(), any());
    }

    @Test
    void importsAnOfferOnceIntoThePipeline() throws Exception {
        ProjectDto project = new ProjectDto();
        project.setId(40L);
        project.setRole("Développeur Java");
        when(importService.importOffer(3L, 7L, null)).thenReturn(project);
        when(importService.importOffer(3L, 9L, null)).thenThrow(new InvalidStateException("This offer is already in the pipeline as project 40"));

        mockMvc.perform(post("/offers/7/import").param("freelanceId", "3"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(40));
        mockMvc.perform(post("/offers/9/import").param("freelanceId", "3")).andExpect(status().isConflict());
    }

    @Test
    void findsTheOfferOfALinkWithTheOpportunityItWouldMake() throws Exception {
        ProjectDto project = new ProjectDto();
        project.setRole("Développeur Java");
        when(linkService.resolve(3L, "https://www.free-work.com/fr/mission/dev-java")).thenReturn(new OfferLinkDto(OfferLinkStatus.FOUND,
            "Free-Work", null, null, new OfferDraftDto(project, "Exemple Conseil", "Free-Work"), null, null));

        mockMvc.perform(post("/offers/link").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://www.free-work.com/fr/mission/dev-java\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FOUND"))
            .andExpect(jsonPath("$.draft.clientName").value("Exemple Conseil"))
            .andExpect(jsonPath("$.draft.project.role").value("Développeur Java"))
            .andExpect(jsonPath("$.projectId").doesNotExist());
        mockMvc.perform(post("/offers/link").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\" \"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/offers/link").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + "a".repeat(2001) + "\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/offers/link").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://x.fr\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void importsAnOfferAsCorrectedInTheProjectForm() throws Exception {
        ProjectDto created = new ProjectDto();
        created.setId(41L);
        when(importService.importOffer(eq(3L), eq(7L), any(ProjectDto.class))).thenReturn(created);

        mockMvc.perform(post("/offers/7/import").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"Tech lead Java\",\"dailyRate\":700,\"status\":\"INTERVIEW\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(41));
        org.mockito.ArgumentCaptor<ProjectDto> sent = org.mockito.ArgumentCaptor.forClass(ProjectDto.class);
        verify(importService).importOffer(eq(3L), eq(7L), sent.capture());
        assertThat(sent.getValue().getRole()).isEqualTo("Tech lead Java");
        // The form's checks apply: a role and a daily rate.
        mockMvc.perform(post("/offers/7/import").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"\",\"dailyRate\":700}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void actsOnSeveralOffersAtOnce() throws Exception {
        when(bulkService.updateAll(3L, new OfferBulkStateRequest(List.of(7L, 8L), true, null, null)))
            .thenReturn(List.of(new OfferStateDto(7L, true, false, false, null), new OfferStateDto(8L, true, false, true, null)));
        when(bulkService.importAll(3L, new OfferBulkImportRequest(List.of(7L, 8L)))).thenReturn(new OfferBulkImportDto(
            List.of(new OfferBulkImportDto.Imported(7L, 41L)), List.of(new OfferBulkImportDto.Imported(8L, 40L)), List.of()));

        mockMvc.perform(put("/offers/states").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"offerIds\":[7,8],\"saved\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1].offerId").value(8))
            .andExpect(jsonPath("$[1].saved").value(true));
        mockMvc.perform(post("/offers/import").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"offerIds\":[7,8]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported[0].projectId").value(41))
            .andExpect(jsonPath("$.alreadyInPipeline[0].offerId").value(8));
        String tooMany = "{\"offerIds\":[" + "1,".repeat(100) + "1]}";
        for (String invalid : List.of("{\"offerIds\":[]}", "{\"saved\":true}", "{\"offerIds\":[7,null]}", tooMany)) {
            mockMvc.perform(put("/offers/states").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/offers/import").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"offerIds\":[" + "1,".repeat(50) + "1]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/offers/states").contentType(MediaType.APPLICATION_JSON).content("{\"offerIds\":[7],\"saved\":true}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void recordsAndForgetsAnApplication() throws Exception {
        OfferApplication application = new OfferApplication(LocalDate.of(2026, 10, 20), ApplicationChannel.SITE_FORM);
        OfferApplicationRequest request = new OfferApplicationRequest(LocalDate.of(2026, 10, 20), ApplicationChannel.SITE_FORM, 7,
            DocumentLanguage.FR);
        when(applicationService.apply(3L, 7L, request)).thenReturn(new OfferStateDto(7L, false, false, true, 40L, null, application));
        when(applicationService.withdraw(3L, 7L)).thenReturn(new OfferStateDto(7L, false, false, true, 40L));

        mockMvc.perform(post("/offers/7/application").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appliedOn\":\"2026-10-20\",\"via\":\"SITE_FORM\",\"followUpInDays\":7,\"language\":\"FR\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedProjectId").value(40))
            .andExpect(jsonPath("$.application.via").value("SITE_FORM"));
        mockMvc.perform(delete("/offers/7/application").param("freelanceId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application").doesNotExist());
        for (String invalid : List.of("{\"via\":\"EMAIL\"}", "{\"appliedOn\":\"2026-10-20\"}",
            "{\"appliedOn\":\"2026-10-20\",\"via\":\"EMAIL\",\"followUpInDays\":0}",
            "{\"appliedOn\":\"2026-10-20\",\"via\":\"EMAIL\",\"followUpInDays\":61}",
            "{\"appliedOn\":\"2026-10-20\",\"via\":\"FAX\"}")) {
            mockMvc.perform(post("/offers/7/application").param("freelanceId", "3").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/offers/7/application").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appliedOn\":\"2026-10-20\",\"via\":\"EMAIL\"}"))
            .andExpect(status().isBadRequest());
        verify(applicationService, never()).apply(any(), eq(7L), eq(new OfferApplicationRequest(LocalDate.of(2026, 10, 20),
            ApplicationChannel.EMAIL, null, null)));
    }

    @Test
    void listsSitesSkillsAndRegions() throws Exception {
        when(searchService.sites(null)).thenReturn(List.of(new OfferSiteDto(OfferSite.YEETS, "Yeets", "https://www.yeets.fr", 12, null, false,
            false)));
        when(searchService.skills("ja")).thenReturn(List.of(new OfferFacetDto("Java", 42)));
        when(searchService.regions()).thenReturn(List.of(new OfferRegionDto("94", "Corse",
            List.of(new OfferRegionDto.Department("2A", "Corse-du-Sud")))));

        mockMvc.perform(get("/offers/sites")).andExpect(status().isOk()).andExpect(jsonPath("$[0].openOffers").value(12));
        mockMvc.perform(get("/offers/skills").param("q", "ja")).andExpect(status().isOk()).andExpect(jsonPath("$[0].value").value("Java"));
        mockMvc.perform(get("/offers/regions")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].departments[0].name").value("Corse-du-Sud"));
        mockMvc.perform(get("/offers/skills").param("q", "x".repeat(61))).andExpect(status().isBadRequest());
    }

    private static OfferDto offer() {
        return new OfferDto(7L, OfferSite.FREE_WORK, "Free-Work", "https://www.free-work.com/7", "Développeur Java",
            "Exemple Conseil", "Refonte d'un SI", null, 550, 650, "Paris", "75008", "75", "Paris", "Île-de-France", "FR", 3,
            WorkMode.HYBRID, 2, 6, LocalDate.of(2026, 11, 2), null, Seniority.SENIOR, List.of("Java", "Kafka"),
            Instant.parse("2026-10-19T08:47:42Z"), OfferDatePrecision.EXACT, null, false, false, false, false, null,
            List.of(new OfferCopyDto(8L, OfferSite.COLLECTIVE, "Collective", "https://www.collective.work/job/8")),
            new OfferMatchDto(83, List.of("Java"), OfferMatchDto.RateFit.MEETS, OfferMatchDto.PlaceFit.NEAR, null), "Call Léa",
            new OfferApplication(LocalDate.of(2026, 10, 20), ApplicationChannel.EMAIL), new OfferRateMarketDto(9, 550, 34, "Java", "Île-de-France"),
            List.of(OfferSuspicion.SAME_TEXT_MANY_PUBLISHERS));
    }
}
