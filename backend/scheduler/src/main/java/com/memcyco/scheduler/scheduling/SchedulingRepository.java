package com.memcyco.scheduler.scheduling;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulingRepository extends JpaRepository<Scheduling, UUID> {}
