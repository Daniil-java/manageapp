package com.kuklin.manageapp.bots.kworkparser.repositories;

import com.kuklin.manageapp.bots.kworkparser.entities.Kwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KworkRepository extends JpaRepository<Kwork, Long> {
    List<Kwork> findAllByKworkIdInAndStatus(List<Long> kworkIds, Kwork.Status status);

}


