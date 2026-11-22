package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.dto.SupportDtos;
import dev.swirlit.indezy.model.SupportAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupportAttachmentRepository extends JpaRepository<SupportAttachment, Long> {

    long countByRequestId(Long requestId);

    /** The requests' files without their content, by request then in the order added. */
    @Query("select new dev.swirlit.indezy.dto.SupportDtos$Attachment(a.id, a.requestId, a.fileName, a.contentType, a.sizeBytes) " +
        "from SupportAttachment a where a.requestId in :requestIds order by a.requestId, a.id")
    List<SupportDtos.Attachment> listFor(@Param("requestIds") Collection<Long> requestIds);

    Optional<SupportAttachment> findByIdAndRequestId(Long id, Long requestId);
}
