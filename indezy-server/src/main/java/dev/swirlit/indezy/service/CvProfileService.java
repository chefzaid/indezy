package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.CareerDtos;
import dev.swirlit.indezy.dto.CvProfileDtos;
import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.CvProfile;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierFormat;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.repository.CvProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * CV profiles (see docs/features.md#cv-profiles): a few named positionings of the same person, each drawn as a dossier
 * de compétences from the profile's facts with its own headline, summary, skill selection and order, and the won
 * missions and past positions it shows, in its order and wording. One is the default, drawn wherever none is chosen.
 */
@Service
@RequiredArgsConstructor
public class CvProfileService {

    /** At most this many profiles per workspace. */
    public static final int PROFILES_MAX = 20;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CvProfileRepository profiles;
    private final DossierService dossiers;
    private final CareerService career;

    @Transactional(readOnly = true)
    public List<CvProfileDtos.Profile> list(Long freelanceId) {
        return profiles.findByFreelanceIdOrderByCreatedAtAscIdAsc(freelanceId).stream().map(CvProfileService::dto).toList();
    }

    /** A new profile; the first one is the default. */
    @Transactional
    public CvProfileDtos.Profile create(Long freelanceId, CvProfileDtos.Details details) {
        if (profiles.countByFreelanceId(freelanceId) >= PROFILES_MAX) {
            throw new InvalidStateException("You already keep " + PROFILES_MAX + " CV profiles: delete one first");
        }
        CvProfile profile = new CvProfile();
        profile.setFreelanceId(freelanceId);
        profile.setDefaultProfile(profiles.findFirstByFreelanceIdAndDefaultProfileTrue(freelanceId).isEmpty());
        write(profile, details);
        return dto(profiles.save(profile));
    }

    @Transactional
    public CvProfileDtos.Profile update(Long freelanceId, Long id, CvProfileDtos.Details details) {
        CvProfile profile = owned(freelanceId, id);
        write(profile, details);
        return dto(profile);
    }

    /** Deletes the profile; when it was the default, the oldest one left becomes it. */
    @Transactional
    public void delete(Long freelanceId, Long id) {
        CvProfile profile = owned(freelanceId, id);
        profiles.delete(profile);
        profiles.flush();
        if (profile.isDefaultProfile()) {
            profiles.findByFreelanceIdOrderByCreatedAtAscIdAsc(freelanceId).stream().findFirst().ifPresent(next -> next.setDefaultProfile(true));
        }
    }

    @Transactional
    public CvProfileDtos.Profile makeDefault(Long freelanceId, Long id) {
        CvProfile chosen = owned(freelanceId, id);
        profiles.findByFreelanceIdOrderByCreatedAtAscIdAsc(freelanceId).forEach(profile -> profile.setDefaultProfile(profile == chosen));
        return dto(chosen);
    }

    /** The profile's headline, summary, skills, won missions and past positions, to pick from and word. */
    @Transactional(readOnly = true)
    public CvProfileDtos.Sources sources(Long userId, Long freelanceId) {
        DossierDto dossier = dossiers.dossier(userId, freelanceId, DossierLayout.NAMED);
        List<Long> missionIds = dossiers.missionIds(freelanceId);
        List<CareerDtos.Experience> experiences = career.experiences(freelanceId);
        List<CvProfileDtos.Source> missions = new ArrayList<>();
        for (int index = 0; index < Math.min(missionIds.size(), dossier.missions().size()); index++) {
            DossierDto.Mission mission = dossier.missions().get(index);
            missions.add(new CvProfileDtos.Source(missionIds.get(index), mission.role(), mission.description()));
        }
        return new CvProfileDtos.Sources(dossier.position(), dossier.summary(), dossier.skills().stream().map(DossierDto.Skill::name).toList(),
            missions, experiences.stream().map(experience -> new CvProfileDtos.Source(experience.id(), experience.title(), experience.description()))
                .toList());
    }

    /** The profile's dossier, a PDF or a Word document, in the profile's language unless another is asked. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile file(Long userId, Long freelanceId, Long id, DossierLayout layout, DocumentLanguage language,
                                              DossierFormat format) {
        CvProfile profile = owned(freelanceId, id);
        return dossiers.render(shaped(userId, freelanceId, profile, layout), language == null ? profile.getLanguage() : language, format);
    }

    /** The dossier as the default profile draws it, or as the profile says without one. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile defaultFile(Long userId, Long freelanceId, DossierLayout layout, DocumentLanguage language,
                                                     DossierFormat format) {
        Optional<CvProfile> chosen = profiles.findFirstByFreelanceIdAndDefaultProfileTrue(freelanceId);
        return chosen.isPresent() ? dossiers.render(shaped(userId, freelanceId, chosen.get(), layout), language, format) :
            dossiers.file(userId, freelanceId, layout, language, format);
    }

    private DossierDto shaped(Long userId, Long freelanceId, CvProfile profile, DossierLayout layout) {
        return shape(dossiers.dossier(userId, freelanceId, layout), dossiers.missionIds(freelanceId),
            career.experiences(freelanceId).stream().map(CareerDtos.Experience::id).toList(), dto(profile));
    }

    /**
     * The dossier with a profile's choices: its headline and summary instead of the profile's, the skills it names in its
     * order (those the profile no longer holds left out), the missions and positions it names in its order with its
     * wording (those gone from the profile left out); a choice left null keeps the profile's. The facts stay the dossier's.
     */
    public static DossierDto shape(DossierDto dossier, List<Long> missionIds, List<Long> positionIds, CvProfileDtos.Profile profile) {
        List<DossierDto.Skill> skills = dossier.skills();
        if (profile.skills() != null) {
            Map<String, DossierDto.Skill> byName = new LinkedHashMap<>();
            dossier.skills().forEach(skill -> byName.putIfAbsent(skill.name().toLowerCase(Locale.ROOT), skill));
            skills = profile.skills().stream().map(name -> byName.remove(name.strip().toLowerCase(Locale.ROOT))).filter(Objects::nonNull).toList();
        }
        return new DossierDto(dossier.name(), blank(profile.headline()) == null ? dossier.position() : profile.headline().strip(), dossier.city(),
            dossier.contact(), blank(profile.summary()) == null ? dossier.summary() : profile.summary().strip(), skills, dossier.languages(),
            dossier.certifications(), items(dossier.missions(), missionIds, profile.missions()),
            items(dossier.positions(), positionIds, profile.positions()), dossier.education());
    }

    private static List<DossierDto.Mission> items(List<DossierDto.Mission> items, List<Long> ids, List<CvProfileDtos.Item> chosen) {
        if (chosen == null) {
            return items;
        }
        Map<Long, DossierDto.Mission> byId = new LinkedHashMap<>();
        for (int index = 0; index < Math.min(items.size(), ids.size()); index++) {
            byId.put(ids.get(index), items.get(index));
        }
        List<DossierDto.Mission> shown = new ArrayList<>();
        for (CvProfileDtos.Item item : chosen) {
            DossierDto.Mission mission = byId.remove(item.id());
            if (mission != null) {
                String text = blank(item.text());
                shown.add(text == null ? mission : new DossierDto.Mission(mission.role(), mission.client(), mission.startsOn(), mission.endsOn(),
                    mission.stack(), DossierService.excerpt(text)));
            }
        }
        return shown;
    }

    private CvProfile owned(Long freelanceId, Long id) {
        return profiles.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("CV profile not found"));
    }

    private static void write(CvProfile profile, CvProfileDtos.Details details) {
        profile.setName(details.name().strip());
        profile.setLanguage(details.language() == null ? DocumentLanguage.FR : details.language());
        profile.setHeadline(blank(details.headline()));
        profile.setSummary(blank(details.summary()));
        profile.setSkills(details.skills() == null ? null : JSON.writeValueAsString(details.skills().stream().map(String::strip).distinct().toList()));
        profile.setMissions(json(details.missions()));
        profile.setPositions(json(details.positions()));
    }

    private static String json(List<CvProfileDtos.Item> items) {
        if (items == null) {
            return null;
        }
        Map<Long, CvProfileDtos.Item> byId = new LinkedHashMap<>();
        items.forEach(item -> byId.putIfAbsent(item.id(), new CvProfileDtos.Item(item.id(), blank(item.text()))));
        return JSON.writeValueAsString(List.copyOf(byId.values()));
    }

    static CvProfileDtos.Profile dto(CvProfile profile) {
        return new CvProfileDtos.Profile(profile.getId(), profile.getName(), profile.isDefaultProfile(), profile.getLanguage(), profile.getHeadline(),
            profile.getSummary(), profile.getSkills() == null ? null : Arrays.asList(JSON.readValue(profile.getSkills(), String[].class)),
            read(profile.getMissions()), read(profile.getPositions()), profile.getUpdatedAt());
    }

    private static List<CvProfileDtos.Item> read(String json) {
        return json == null ? null : Arrays.asList(JSON.readValue(json, CvProfileDtos.Item[].class));
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
