package com.kuklin.manageapp.bots.channelposter.repositories.parser;

import com.kuklin.manageapp.bots.channelposter.entities.parser.Subreddit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubredditRepository extends JpaRepository<Subreddit, Long> {

    List<Subreddit> findAllByActiveTrue();
}
