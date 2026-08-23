package com.civicconnect.backend.dto;

/**
 * Aggregate KPIs for the officer dashboard's stat row: how many
 * complaints are open, how many are closing in on their SLA, how many
 * have already breached it, and what fraction of resolved complaints
 * were resolved on time. Computed server-side (see ComplaintService)
 * so the officer/admin dashboard doesn't have to page through every
 * complaint just to show four numbers.
 *
 * Scoping (ward-only vs. citywide) is decided by the caller's role
 * before this DTO is built, not by anything in the DTO itself — an
 * OFFICER always gets their own ward's numbers, an ADMIN gets everyone's.
 */
public class OfficerStatsResponse {
    public int open;
    public int nearSla;
    public int breached;
    public int closedCount;
    // Null when there's no closed-complaint history yet to compute a
    // percentage from, rather than a misleading 0% or 100%.
    public Integer onTimePercentage;

    public OfficerStatsResponse() {}

    public OfficerStatsResponse(int open, int nearSla, int breached, int closedCount, Integer onTimePercentage) {
        this.open = open;
        this.nearSla = nearSla;
        this.breached = breached;
        this.closedCount = closedCount;
        this.onTimePercentage = onTimePercentage;
    }
}
