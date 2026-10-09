package com.kuklin.manageapp.bots.hhparserbot.repositories;

import com.kuklin.manageapp.bots.hhparserbot.entities.WorkFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkFilterRepository extends JpaRepository<WorkFilter, Long> {
    List<WorkFilter> findAllByHhUserInfoId(long hhUserInfo);

    Optional<WorkFilter> findByHhUserInfoIdAndUrl(Long hhUserInfoId, String url);
}
