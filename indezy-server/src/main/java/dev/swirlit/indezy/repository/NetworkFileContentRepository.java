package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkFileContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface NetworkFileContentRepository extends JpaRepository<NetworkFileContent, Long> {

    @Modifying
    @Query("DELETE FROM NetworkFileContent c WHERE c.fileId IN :ids")
    void deleteByFileIdIn(@Param("ids") Collection<Long> ids);
}
