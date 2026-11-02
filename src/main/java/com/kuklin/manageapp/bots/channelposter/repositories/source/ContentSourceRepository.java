package com.kuklin.manageapp.bots.channelposter.repositories.source;

import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentSourceRepository extends JpaRepository<ContentSource, Long> {

    List<ContentSource> findAllByActiveTrueOrderByIdAsc();

    List<ContentSource> findAllByOrderByIdAsc();

    boolean existsByTypeAndAddress(ContentSource.SourceType type, String address);
}
