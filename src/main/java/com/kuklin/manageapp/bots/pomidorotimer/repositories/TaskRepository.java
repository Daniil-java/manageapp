package com.kuklin.manageapp.bots.pomidorotimer.repositories;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<List<Task>> findTasksByUserEntityIdAndParentIsNull(Long id);

    List<Task> findAllByUserEntityIdAndParentIsNull(long userId, Pageable pageable);

    List<Task> findAllByParentId(long taskId, Pageable pageable);

    @Query("SELECT t FROM Task t JOIN t.timers timer WHERE timer.id = :timerId")
    @EntityGraph(attributePaths = {"childTasks"})
    List<Task> findAllByTimerId(@Param("timerId") Long timerId);

    @Query("SELECT t FROM Task t WHERE t.userEntity.id = :userId and t.parent is null and t.status <> 'DONE'")
    List<Task> findAllNotDoneByUserEntityIdAndParentIsNull(@Param("userId") long userId, Pageable pageable);
}
