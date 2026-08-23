package com.civicconnect.backend.service;

import com.civicconnect.backend.model.Complaint;
import com.civicconnect.backend.model.ComplaintStatusHistory;
import com.civicconnect.backend.repository.ComplaintRepository;
import com.civicconnect.backend.repository.ComplaintStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs periodically and flags any complaint whose SLA deadline has passed
 * while it's still open. This is intentionally a flag ("escalated = true"
 * plus a status-history note), not a reassignment to a different
 * authority — the platform doesn't model a "senior officer" hierarchy, so
 * inventing an escalation target would be dishonest about what actually
 * happens. What this DOES give the officer dashboard is a real, live
 * signal to visually flag overdue complaints, which is the part that
 * actually matters for accountability.
 */
@Service
public class SlaEscalationService {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

    private final ComplaintRepository complaintRepository;
    private final ComplaintStatusHistoryRepository historyRepository;

    public SlaEscalationService(ComplaintRepository complaintRepository,
                                 ComplaintStatusHistoryRepository historyRepository) {
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
    }

    // Every 5 minutes is frequent enough to feel "live" in a demo without
    // hammering the database. Trivial to tune via
    // SLA_ESCALATION_INTERVAL_MS if needed.
    @Scheduled(fixedRateString = "${app.sla.escalation-interval-ms:300000}")
    public void escalateBreachedComplaints() {
        List<Complaint> breached = complaintRepository.findNewlyBreachedComplaints();
        if (breached.isEmpty()) {
            return;
        }
        for (Complaint c : breached) {
            c.setEscalated(true);
            complaintRepository.save(c);

            ComplaintStatusHistory h = new ComplaintStatusHistory();
            h.setComplaint(c);
            h.setStatus(c.getStatus());
            h.setNote("SLA deadline passed — auto-escalated for officer attention.");
            historyRepository.save(h);
        }
        log.info("SLA escalation job: flagged {} complaint(s) as escalated", breached.size());
    }
}
