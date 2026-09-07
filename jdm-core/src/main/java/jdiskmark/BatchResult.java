package jdiskmark;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BatchResult {

    public enum DriveStatus {
        COMPLETED, SKIPPED, RETRIED_THEN_COMPLETED, RETRIED_THEN_SKIPPED
    }

    public record RunResult(
        File driveLocation,
        String driveModel,
        BenchmarkProfile profile,
        Benchmark benchmark,
        DriveStatus status,
        String errorMessage
    ) {
        public boolean isSuccess() {
            return status == DriveStatus.COMPLETED || status == DriveStatus.RETRIED_THEN_COMPLETED;
        }
    }

    private final List<RunResult> results = new ArrayList<>();
    private final List<BenchmarkProfile> profiles;
    private final UUID batchId;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;

    public BatchResult(List<BenchmarkProfile> profiles, UUID batchId) {
        this.profiles = List.copyOf(profiles);
        this.batchId = batchId;
        this.startTime = LocalDateTime.now();
    }

    public void addResult(RunResult result) {
        results.add(result);
    }

    public void recordEndTime() {
        this.endTime = LocalDateTime.now();
    }

    public List<RunResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public List<RunResult> getSuccessfulResults() {
        return results.stream().filter(RunResult::isSuccess).toList();
    }

    public List<RunResult> getResultsForProfile(BenchmarkProfile profile) {
        return results.stream().filter(r -> r.profile() == profile).toList();
    }

    public List<BenchmarkProfile> getProfiles() { return profiles; }
    public UUID getBatchId() { return batchId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }

    public Duration getTotalDuration() {
        if (endTime == null) return Duration.ZERO;
        return Duration.between(startTime, endTime);
    }
}
