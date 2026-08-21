package com.civicconnect.backend.repository;

import com.civicconnect.backend.model.ComplaintStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, Integer> {
    List<ComplaintStatusHistory> findByComplaintIdOrderByCreatedAtAsc(Integer complaintId);
}
