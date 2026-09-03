package com.valor.response;

import java.util.List;

public record AdminDashboardResponse(
        long totalJobsToday,
        long jobsScheduledTomorrow,
        long completedJobs,
        long pendingJobs,
        long emergencyJobs,
        long totalCustomers,
        long totalTechnicians,
        long totalLifts,
        long totalAmcs,
        List<ServiceRequestResponse> todaysJobs,
        List<ServiceRequestResponse> tomorrowJobs,
        List<ServiceRequestResponse> pendingJobList,
        List<ServiceRequestResponse> inProgressJobs,
        List<ServiceRequestResponse> completedJobList,
        List<ServiceRequestResponse> emergencyJobList
) {
}