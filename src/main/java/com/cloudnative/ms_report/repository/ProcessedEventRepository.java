package com.cloudnative.ms_report.repository;

import com.cloudnative.ms_report.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {}
