package com.kuklin.manageapp.bots.channelposter.repositories;

import com.kuklin.manageapp.bots.channelposter.entities.ChannelDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ChannelDailyStatsRepository extends JpaRepository<ChannelDailyStats, LocalDate> {
}
