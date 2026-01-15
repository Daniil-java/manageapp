package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.OAuthLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OAuthLinkRepository extends JpaRepository<OAuthLink, UUID> {
}
