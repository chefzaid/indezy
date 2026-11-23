package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.CareerDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.ProfileEducation;
import dev.swirlit.indezy.model.ProfileExperience;
import dev.swirlit.indezy.repository.ProfileEducationRepository;
import dev.swirlit.indezy.repository.ProfileExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * The profile's past positions and education (see docs/features.md#experience-and-education), each workspace's own: the
 * running and latest positions first, the latest schools first.
 */
@Service
@RequiredArgsConstructor
public class CareerService {

    static final int MAX_ENTRIES = 100;
    private static final Comparator<ProfileExperience> LATEST_POSITION = Comparator
        .comparing((ProfileExperience experience) -> experience.getEndsOn() == null ? 0 : 1)
        .thenComparing(ProfileExperience::getStartsOn, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(ProfileExperience::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    private static final Comparator<ProfileEducation> LATEST_SCHOOL = Comparator
        .comparing(ProfileEducation::getEndYear, Comparator.nullsFirst(Comparator.reverseOrder()))
        .thenComparing(ProfileEducation::getStartYear, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(ProfileEducation::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final ProfileExperienceRepository experiences;
    private final ProfileEducationRepository educations;

    @Transactional(readOnly = true)
    public List<CareerDtos.Experience> experiences(Long freelanceId) {
        return experiences.findByFreelanceId(freelanceId).stream().sorted(LATEST_POSITION).map(CareerService::dto).toList();
    }

    @Transactional
    public CareerDtos.Experience createExperience(Long freelanceId, CareerDtos.ExperienceDetails details) {
        if (experiences.findByFreelanceId(freelanceId).size() >= MAX_ENTRIES) {
            throw new IllegalArgumentException("A profile holds " + MAX_ENTRIES + " positions at most");
        }
        ProfileExperience experience = new ProfileExperience();
        experience.setFreelanceId(freelanceId);
        return dto(experiences.save(apply(experience, details)));
    }

    @Transactional
    public CareerDtos.Experience updateExperience(Long freelanceId, Long id, CareerDtos.ExperienceDetails details) {
        return dto(experiences.save(apply(experience(freelanceId, id), details)));
    }

    @Transactional
    public void deleteExperience(Long freelanceId, Long id) {
        experiences.delete(experience(freelanceId, id));
    }

    @Transactional(readOnly = true)
    public List<CareerDtos.Education> educations(Long freelanceId) {
        return educations.findByFreelanceId(freelanceId).stream().sorted(LATEST_SCHOOL).map(CareerService::dto).toList();
    }

    @Transactional
    public CareerDtos.Education createEducation(Long freelanceId, CareerDtos.EducationDetails details) {
        if (educations.findByFreelanceId(freelanceId).size() >= MAX_ENTRIES) {
            throw new IllegalArgumentException("A profile holds " + MAX_ENTRIES + " schools at most");
        }
        ProfileEducation education = new ProfileEducation();
        education.setFreelanceId(freelanceId);
        return dto(educations.save(apply(education, details)));
    }

    @Transactional
    public CareerDtos.Education updateEducation(Long freelanceId, Long id, CareerDtos.EducationDetails details) {
        return dto(educations.save(apply(education(freelanceId, id), details)));
    }

    @Transactional
    public void deleteEducation(Long freelanceId, Long id) {
        educations.delete(education(freelanceId, id));
    }

    private ProfileExperience experience(Long freelanceId, Long id) {
        return experiences.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Position not found"));
    }

    private ProfileEducation education(Long freelanceId, Long id) {
        return educations.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("School not found"));
    }

    private static ProfileExperience apply(ProfileExperience experience, CareerDtos.ExperienceDetails details) {
        if (details.startsOn() != null && details.endsOn() != null && details.endsOn().isBefore(details.startsOn())) {
            throw new IllegalArgumentException("A position ends on or after its start");
        }
        experience.setEmployer(details.employer().strip());
        experience.setTitle(details.title().strip());
        experience.setStartsOn(details.startsOn());
        experience.setEndsOn(details.endsOn());
        experience.setDescription(blankToNull(details.description()));
        experience.setStack(blankToNull(details.stack()));
        experience.setShownPublicly(Boolean.TRUE.equals(details.shownPublicly()));
        return experience;
    }

    private static ProfileEducation apply(ProfileEducation education, CareerDtos.EducationDetails details) {
        if (details.startYear() != null && details.endYear() != null && details.endYear() < details.startYear()) {
            throw new IllegalArgumentException("A school ends in or after the year it starts");
        }
        education.setSchool(details.school().strip());
        education.setDegree(blankToNull(details.degree()));
        education.setStartYear(details.startYear());
        education.setEndYear(details.endYear());
        education.setShownPublicly(Boolean.TRUE.equals(details.shownPublicly()));
        return education;
    }

    static CareerDtos.Experience dto(ProfileExperience experience) {
        return new CareerDtos.Experience(experience.getId(), experience.getEmployer(), experience.getTitle(), experience.getStartsOn(),
            experience.getEndsOn(), experience.getDescription(), experience.getStack(), experience.isShownPublicly());
    }

    static CareerDtos.Education dto(ProfileEducation education) {
        return new CareerDtos.Education(education.getId(), education.getSchool(), education.getDegree(), education.getStartYear(),
            education.getEndYear(), education.isShownPublicly());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
