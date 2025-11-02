package jdiskmark;

import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() throws Exception {
        return new String[]{
            "JDiskMark " + App.VERSION,
            " Java " + System.getProperty("java.version")
        };
    }
}