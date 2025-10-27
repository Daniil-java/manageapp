package com.kuklin.manageapp.bots.kworkparser.repositories;

import com.kuklin.manageapp.bots.kworkparser.entities.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {
    Optional<Url> findUrlByUrl(String url);
}
