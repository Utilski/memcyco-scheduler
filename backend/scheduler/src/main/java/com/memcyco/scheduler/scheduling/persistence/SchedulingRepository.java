package com.memcyco.scheduler.scheduling.persistence;

import com.memcyco.scheduler.scheduling.domain.Scheduling;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchedulingRepository
    extends JpaRepository<Scheduling, UUID>, JpaSpecificationExecutor<Scheduling> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select scheduling from Scheduling scheduling where scheduling.id = :id")
  Optional<Scheduling> findForUpdate(@Param("id") UUID id);
}
