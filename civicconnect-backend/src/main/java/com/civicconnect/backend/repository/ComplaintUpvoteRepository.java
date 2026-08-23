package com.civicconnect.backend.repository;

import com.civicconnect.backend.model.ComplaintUpvote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintUpvoteRepository extends JpaRepository<ComplaintUpvote, Integer> {
    boolean existsByComplaintIdAndVoterKey(Integer complaintId, String voterKey);
}
