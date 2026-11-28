package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.CvProfileDtos;
import dev.swirlit.indezy.dto.CvShareDtos;
import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.CvShare;
import dev.swirlit.indezy.model.enums.DossierFormat;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.repository.CvShareRepository;
import dev.swirlit.indezy.util.Tokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CV profiles shared through a link of their own (see docs/features.md#cv-profiles): the token is shown once and only
 * its SHA-256 is kept; the link opens the profile's dossier, named or anonymized as chosen, until it expires (1 to 30
 * days) or is revoked, each opening and download counted. The dossier is drawn when it is opened, from the profile as
 * it is then.
 */
@Service
@RequiredArgsConstructor
public class CvShareService {

    static final int DEFAULT_DAYS = 7;
    static final int MAX_ACTIVE = 20;

    private final CvShareRepository shares;
    private final CvProfileService profiles;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CvShareDtos.Share> list(Long freelanceId) {
        Map<Long, String> names = profiles.list(freelanceId).stream()
            .collect(Collectors.toMap(CvProfileDtos.Profile::id, CvProfileDtos.Profile::name, (first, second) -> first));
        return shares.findByFreelanceIdOrderByCreatedAtDescIdDesc(freelanceId).stream().map(share -> dto(share, names)).toList();
    }

    @Transactional
    public CvShareDtos.CreatedShare create(Long userId, Long freelanceId, CvShareDtos.ShareRequest request) {
        CvProfileDtos.Profile profile = profiles.list(freelanceId).stream().filter(candidate -> candidate.id().equals(request.profileId()))
            .findFirst().orElseThrow(() -> new ResourceNotFoundException("CV profile not found"));
        if (list(freelanceId).stream().filter(CvShareDtos.Share::active).count() >= MAX_ACTIVE) {
            throw new InvalidStateException("Up to " + MAX_ACTIVE + " links can be open at once: revoke one first");
        }
        String token = Tokens.random();
        CvShare share = new CvShare();
        share.setFreelanceId(freelanceId);
        share.setUserId(userId);
        share.setProfileId(profile.id());
        share.setRecipient(request.recipient().strip());
        share.setLayout(request.layout() == null ? DossierLayout.NAMED : request.layout());
        share.setTokenHash(Tokens.sha256Hex(token));
        share.setExpiresAt(clock.instant().plus(Duration.ofDays(request.days() == null ? DEFAULT_DAYS : request.days())));
        return new CvShareDtos.CreatedShare(dto(shares.save(share), Map.of(profile.id(), profile.name())), token);
    }

    /** Closes a link for good; a revoked link stays listed. */
    @Transactional
    public CvShareDtos.Share revoke(Long freelanceId, Long id) {
        CvShare share = shares.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Link not found"));
        if (share.getRevokedAt() == null) {
            share.setRevokedAt(clock.instant());
        }
        return dto(share, Map.of());
    }

    /** What the link shows, counted as an opening. */
    @Transactional
    public CvShareDtos.PublicShare open(String token) {
        CvShare share = valid(token);
        share.setOpenCount(share.getOpenCount() + 1);
        share.setLastOpenedAt(clock.instant());
        CvProfileDtos.Profile profile = profile(share);
        DossierDto dossier = profiles.shapedDossier(share.getUserId(), share.getFreelanceId(), profile.id(), share.getLayout());
        return new CvShareDtos.PublicShare(dossier.name(), dossier.position(), profile.language(), share.getLayout(), share.getExpiresAt());
    }

    /** The shared dossier, as a PDF or a Word document, counted as a download. */
    @Transactional
    public ActivityReportService.PdfFile file(String token, DossierFormat format) {
        CvShare share = valid(token);
        share.setDownloadCount(share.getDownloadCount() + 1);
        return profiles.file(share.getUserId(), share.getFreelanceId(), profile(share).id(), share.getLayout(), null, format);
    }

    private CvProfileDtos.Profile profile(CvShare share) {
        return profiles.list(share.getFreelanceId()).stream().filter(profile -> profile.id().equals(share.getProfileId())).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Link invalid or expired"));
    }

    /** A link that still opens; any other token reads as not found, whatever the reason. */
    private CvShare valid(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("Link invalid or expired");
        }
        return shares.findByTokenHash(Tokens.sha256Hex(token.strip()))
            .filter(share -> share.getRevokedAt() == null && share.getExpiresAt().isAfter(clock.instant()))
            .orElseThrow(() -> new ResourceNotFoundException("Link invalid or expired"));
    }

    private CvShareDtos.Share dto(CvShare share, Map<Long, String> names) {
        boolean active = share.getRevokedAt() == null && share.getExpiresAt().isAfter(clock.instant());
        return new CvShareDtos.Share(share.getId(), share.getProfileId(), names.get(share.getProfileId()), share.getRecipient(), share.getLayout(),
            share.getCreatedAt(), share.getExpiresAt(), share.getRevokedAt(), active, share.getOpenCount(), share.getDownloadCount(),
            share.getLastOpenedAt());
    }
}
