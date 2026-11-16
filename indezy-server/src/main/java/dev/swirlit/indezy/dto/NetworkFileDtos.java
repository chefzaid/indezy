package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.NetworkFile;
import dev.swirlit.indezy.model.enums.NetworkFileKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The images and documents of the network's posts and messages. */
public final class NetworkFileDtos {

    private NetworkFileDtos() {
    }

    /**
     * A file as its reader sees it, with short-lived signed addresses (relative to the API) for the file and, for a PDF,
     * the picture of its first page.
     */
    public record File(Long id, NetworkFileKind kind, String name, String contentType, long size, String alt, Integer width,
                       Integer height, Integer pages, String url, String previewUrl) {
    }

    /** A file just uploaded, with the workspace's names its text holds, for the member to read before it goes out. */
    public record Uploaded(File file, List<NetworkDtos.ConfidentialMatch> confidential) {

        public Uploaded {
            confidential = List.copyOf(confidential);
        }
    }

    /** A file a post or a message carries, with its description when it is an image (required then). */
    public record Ref(@NotNull Long id, @Size(max = NetworkFile.ALT_MAX) String alt) {
    }
}
