package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.GeoPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GeoPlaceRepository extends JpaRepository<GeoPlace, String> {
}
