package com.valor.response;

public record ReportSummaryResponse(
        long totalCustomers,
        long totalLifts,
        long totalServiceRequests,
        long pendingJobs,
        long completedJobs,
        long emergencyJobs,
        long totalTechnicians,
        long totalAmcs,
        long totalInventoryItems,
        long lowStockItems,
        long totalNotifications,
        long totalPayments,
        long totalAttendanceRecords
) {
}