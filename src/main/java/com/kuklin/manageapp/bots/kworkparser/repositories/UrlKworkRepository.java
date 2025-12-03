package com.kuklin.manageapp.bots.kworkparser.repositories;

import com.kuklin.manageapp.bots.kworkparser.entities.UrlKwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UrlKworkRepository extends JpaRepository<UrlKwork, Long> {
    Optional<UrlKwork> findUrlKworkByUrlIdAndKworkId(Long urlId, Long kworkId);
    List<UrlKwork> findAllByUrlId(Long urlId);
}
