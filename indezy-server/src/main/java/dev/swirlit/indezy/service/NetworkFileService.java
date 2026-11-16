package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.NetworkFileDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.media.MalwareScanner;
import dev.swirlit.indezy.media.NetworkDocuments;
import dev.swirlit.indezy.media.NetworkImages;
import dev.swirlit.indezy.model.NetworkFile;
import dev.swirlit.indezy.model.NetworkFileContent;
import dev.swirlit.indezy.model.enums.NetworkFileKind;
import dev.swirlit.indezy.repository.NetworkFileContentRepository;
import dev.swirlit.indezy.repository.NetworkFileRepository;
import dev.swirlit.indezy.util.AttachmentTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The images and documents of the network's posts and messages (see docs/features.md#member-network), kept in the
 * database: uploaded one at a time (images made anew, documents checked and scanned for malware), then attached when the post or the message
 * goes out, up to 4 images and 3 documents; served through short-lived signed addresses; those never attached are
 * dropped after a day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkFileService {

    public static final int MAX_IMAGES = 4;
    public static final int MAX_DOCUMENTS = 3;
    /** Uploads a member makes in a day, at most. */
    static final int DAILY_UPLOADS = 60;
    static final Duration KEPT_UNATTACHED = Duration.ofDays(1);
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final NetworkService network;
    private final NetworkFileRepository files;
    private final NetworkFileContentRepository contents;
    private final NetworkFileLinks links;
    private final NetworkConfidentialityService confidentiality;
    private final MalwareScanner scanner;
    private final Clock clock;

    /** A file as served: its name, type, bytes, whether the browser shows it, and how long it may keep it. */
    public record Served(String name, String contentType, byte[] content, boolean inline, long maxAgeSeconds) {

        public Served {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    /** Uploads an image or a document, refused (400) with the reason, or (409) beyond 60 a day. */
    @Transactional
    public NetworkFileDtos.Uploaded upload(Long userId, MultipartFile upload) {
        network.requireMember(userId);
        if (files.countByOwnerIdAndCreatedAtAfter(userId, LocalDateTime.now(clock).minusDays(1)) >= DAILY_UPLOADS) {
            throw new InvalidStateException("60 files a day at most: try again tomorrow");
        }
        if (upload.isEmpty() || upload.getSize() > NetworkFile.MAX_BYTES) {
            throw new IllegalArgumentException("A file holds 10 MB at most");
        }
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read", e);
        }
        String name = name(upload.getOriginalFilename());
        boolean image = AttachmentTypes.detect(name, bytes).filter(type -> IMAGE_TYPES.contains(type.contentType())).isPresent();
        NetworkFile file = new NetworkFile();
        file.setOwnerId(userId);
        NetworkFileContent content = new NetworkFileContent();
        List<NetworkDtos.ConfidentialMatch> found = List.of();
        if (image) {
            NetworkImages.Image made = NetworkImages.process(bytes);
            file.setKind(NetworkFileKind.IMAGE);
            file.setContentType(made.contentType());
            file.setWidth(made.width());
            file.setHeight(made.height());
            file.setFileName(renamed(name, made.contentType().equals("image/png") ? "png" : "jpg"));
            content.setContent(made.content());
        } else {
            NetworkDocuments.Type type = NetworkDocuments.detect(name, bytes);
            scanner.scan(bytes).ifPresent(signature -> {
                log.warn("Member {} uploaded a document holding {}: refused", userId, signature);
                throw new IllegalArgumentException("This document holds malware (" + signature + ") and was not added");
            });
            file.setKind(NetworkFileKind.DOCUMENT);
            file.setContentType(type.contentType());
            file.setFileName(renamed(name, type.extension()));
            content.setContent(bytes);
            NetworkDocuments.preview(type, bytes).ifPresent(preview -> {
                content.setPreview(preview.png());
                file.setPages(preview.pages());
            });
            String text = NetworkDocuments.text(type, bytes);
            found = text.isEmpty() ? List.of() : confidentiality.check(userId, text);
        }
        file.setHasPreview(content.getPreview() != null);
        file.setSizeBytes(content.getContent().length);
        files.save(file);
        content.setFileId(file.getId());
        contents.save(content);
        log.info("Member {} uploaded network file {} ({}, {} bytes)", userId, file.getId(), file.getKind(), file.getSizeBytes());
        return new NetworkFileDtos.Uploaded(view(file, clock.instant()), found);
    }

    /** Drops one of the member's files not attached yet; one attached, another's or an unknown one is not found. */
    @Transactional
    public void discard(Long userId, Long fileId) {
        NetworkFile file = files.findById(fileId).filter(found -> found.getOwnerId().equals(userId) && !found.isAttached())
            .orElseThrow(() -> new ResourceNotFoundException("No such file"));
        drop(List.of(file.getId()));
    }

    /** Attaches the member's files to a post. */
    void attachToPost(Long userId, List<NetworkFileDtos.Ref> refs, Long postId) {
        attach(userId, refs, file -> file.setPostId(postId));
    }

    /** Attaches the member's files to a message. */
    void attachToMessage(Long userId, List<NetworkFileDtos.Ref> refs, Long messageId) {
        attach(userId, refs, file -> file.setMessageId(messageId));
    }

    /** Keeps the member's files with their post held for review. */
    void attachToHeld(Long userId, List<NetworkFileDtos.Ref> refs, Long heldId) {
        attach(userId, refs, file -> file.setHeldId(heldId));
    }

    /** Moves the files of a held post to the post it became. */
    void release(Long heldId, Long postId) {
        files.findByHeldIdOrderByPositionAscIdAsc(heldId).forEach(file -> {
            file.setHeldId(null);
            file.setPostId(postId);
        });
    }

    void deleteForPost(Long postId) {
        drop(files.findByPostIdInOrderByPositionAscIdAsc(List.of(postId)).stream().map(NetworkFile::getId).toList());
    }

    void deleteForMessage(Long messageId) {
        drop(files.findByMessageIdInOrderByPositionAscIdAsc(List.of(messageId)).stream().map(NetworkFile::getId).toList());
    }

    void deleteForHeld(Long heldId) {
        drop(files.findByHeldIdOrderByPositionAscIdAsc(heldId).stream().map(NetworkFile::getId).toList());
    }

    /** The files of each post, in their order. */
    Map<Long, List<NetworkFileDtos.File>> forPosts(Collection<Long> postIds) {
        return grouped(postIds.isEmpty() ? List.of() : files.findByPostIdInOrderByPositionAscIdAsc(postIds), NetworkFile::getPostId);
    }

    /** The files of each message, in their order. */
    Map<Long, List<NetworkFileDtos.File>> forMessages(Collection<Long> messageIds) {
        return grouped(messageIds.isEmpty() ? List.of() : files.findByMessageIdInOrderByPositionAscIdAsc(messageIds), NetworkFile::getMessageId);
    }

    /** A file through its signed address; not found when the address is wrong or expired, or the file is gone. */
    @Transactional(readOnly = true)
    public Served serve(Long fileId, String variant, long expires, String signature) {
        Instant now = clock.instant();
        boolean preview = NetworkFileLinks.PREVIEW.equals(variant);
        if (!(preview || NetworkFileLinks.FULL.equals(variant)) || !links.valid(fileId, variant, expires, signature, now)) {
            throw new ResourceNotFoundException("No such file");
        }
        NetworkFile file = files.findById(fileId).orElseThrow(() -> new ResourceNotFoundException("No such file"));
        NetworkFileContent content = contents.findById(fileId).orElseThrow(() -> new ResourceNotFoundException("No such file"));
        long maxAge = Math.max(0, expires - now.getEpochSecond());
        if (preview) {
            if (content.getPreview() == null) {
                throw new ResourceNotFoundException("No such file");
            }
            String type = isPng(content.getPreview()) ? "image/png" : "image/jpeg";
            return new Served(renamed(file.getFileName(), type.equals("image/png") ? "png" : "jpg"), type, content.getPreview(), true, maxAge);
        }
        return new Served(file.getFileName(), file.getContentType(), content.getContent(), AttachmentTypes.isInline(file.getContentType()), maxAge);
    }

    /** Drops every night the files uploaded more than a day ago and never attached. */
    @Scheduled(cron = "0 40 3 * * *", zone = "Europe/Paris")
    @Transactional
    public void dropUnattached() {
        List<Long> stale = files.unattachedBefore(LocalDateTime.now(clock).minus(KEPT_UNATTACHED));
        drop(stale);
        if (!stale.isEmpty()) {
            log.info("Dropped {} network files never attached", stale.size());
        }
    }

    /** Attaches the files, each the member's and not attached yet, 4 images and 3 documents at most, each image described. */
    private void attach(Long userId, List<NetworkFileDtos.Ref> refs, Consumer<NetworkFile> target) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        List<NetworkFile> attached = new ArrayList<>();
        for (NetworkFileDtos.Ref ref : refs) {
            if (!seen.add(ref.id())) {
                continue;
            }
            NetworkFile file = files.findById(ref.id()).filter(found -> found.getOwnerId().equals(userId) && !found.isAttached())
                .orElseThrow(() -> new ResourceNotFoundException("No such file: upload it again"));
            String alt = ref.alt() == null ? "" : ref.alt().strip();
            if (file.isImage() && alt.isEmpty()) {
                throw new IllegalArgumentException("Each image needs a description, for those who cannot see it");
            }
            file.setAltText(file.isImage() ? alt : null);
            attached.add(file);
        }
        if (attached.stream().filter(NetworkFile::isImage).count() > MAX_IMAGES ||
            attached.stream().filter(file -> !file.isImage()).count() > MAX_DOCUMENTS) {
            throw new IllegalArgumentException("4 images and 3 documents at most");
        }
        for (int i = 0; i < attached.size(); i++) {
            attached.get(i).setPosition(i);
            target.accept(attached.get(i));
        }
    }

    private Map<Long, List<NetworkFileDtos.File>> grouped(List<NetworkFile> found, Function<NetworkFile, Long> by) {
        Instant now = clock.instant();
        return found.stream().collect(Collectors.groupingBy(by, Collectors.mapping(file -> view(file, now), Collectors.toList())));
    }

    private NetworkFileDtos.File view(NetworkFile file, Instant now) {
        return new NetworkFileDtos.File(file.getId(), file.getKind(), file.getFileName(), file.getContentType(), file.getSizeBytes(),
            file.getAltText(), file.getWidth(), file.getHeight(), file.getPages(), links.url(file.getId(), NetworkFileLinks.FULL, now),
            Boolean.TRUE.equals(file.getHasPreview()) ? links.url(file.getId(), NetworkFileLinks.PREVIEW, now) : null);
    }

    private void drop(List<Long> ids) {
        if (!ids.isEmpty()) {
            contents.deleteByFileIdIn(ids);
            files.deleteByIdIn(ids);
        }
    }

    /** The name kept: its last part, without control characters, 200 characters at most. */
    private static String name(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name.isEmpty() ? "file" : name;
    }

    /** The name with the extension its content has. */
    private static String renamed(String name, String extension) {
        String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        return (base.isEmpty() ? "file" : base) + "." + extension;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length > 4 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }
}
