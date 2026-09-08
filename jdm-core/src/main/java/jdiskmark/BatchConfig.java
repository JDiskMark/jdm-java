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
        App.locationDir = driveLocation;
        App.dataDir = new File(driveLocation.getAbsolutePath() + File.separator + App.DATADIRNAME);
    }

    public int totalRuns() {
        return drives.size() * profiles.size();
    }
}
