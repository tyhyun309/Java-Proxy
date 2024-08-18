package com.example.demo.repository;

import com.example.demo.model.ProxyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProxyLogRepository extends JpaRepository<ProxyLog, Long> {
