package jdiskmark;

import java.io.File;
import java.util.List;

public record BatchConfig(
    List<File> drives,
    List<BenchmarkProfile> profiles,
    int cooldownSeconds
) {
    public static BatchConfig of(List<File> drives, List<BenchmarkProfile> profiles, int cooldownSeconds) {
        return new BatchConfig(List.copyOf(drives), List.copyOf(profiles), cooldownSeconds);
    }

    public void applyProfileToApp(BenchmarkProfile profile, File driveLocation) {
        App.loadProfile(profile);
        File resolved = DriveChecker.resolveLocationForRoot(driveLocation);
        if (resolved == null) {
            throw new IllegalStateException(
                    "No writable location found on " + driveLocation.getAbsolutePath());
        }
        App.locationDir = resolved;
        App.dataDir = new File(resolved.getAbsolutePath() + File.separator + App.DATADIRNAME);
    }

    public int totalRuns() {
        return drives.size() * profiles.size();
    }
}
