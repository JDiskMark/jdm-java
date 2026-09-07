package jdiskmark;

public sealed interface BatchEvent {
    record RunStarted(int runIndex, int totalRuns, String drivePath, String driveModel, BenchmarkProfile profile) implements BatchEvent {}
    record RunProgress(int runIndex, int percent) implements BatchEvent {}
    record RunCompleted(int runIndex, Benchmark result, BenchmarkProfile profile) implements BatchEvent {}
    record RunRetrying(int runIndex, String errorMessage, BenchmarkProfile profile) implements BatchEvent {}
    record RunSkipped(int runIndex, String errorMessage, BenchmarkProfile profile) implements BatchEvent {}
    record CooldownStarted(int runIndex, int cooldownSeconds) implements BatchEvent {}
    record CooldownTick(int secondsRemaining) implements BatchEvent {}
    record BatchCompleted(BatchResult finalResult) implements BatchEvent {}
    record BatchCancelled(BatchResult partialResult) implements BatchEvent {}
}
