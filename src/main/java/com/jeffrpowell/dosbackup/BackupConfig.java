package com.jeffrpowell.dosbackup;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class BackupConfig {
    private final WorkerThread parentThread;
    private final Path destinationRoot;
    private final boolean backupAllFiles;
    private final boolean deleteDestinationFiles;
    private final Set<Path> expectedDestinationFiles; //used to compare against destination files to see if any should be deleted from the destination

    public BackupConfig(WorkerThread parentThread, Path destinationRoot, boolean backupAllFiles, boolean deleteDestinationFiles) {
        this.parentThread = parentThread;
        this.destinationRoot = destinationRoot;
        this.backupAllFiles = backupAllFiles;
        this.deleteDestinationFiles = deleteDestinationFiles;
        this.expectedDestinationFiles = new HashSet<>();
    }

    public WorkerThread getParentThread() {
        return parentThread;
    }

    public Path getDestinationRoot() {
        return destinationRoot;
    }

    public boolean isBackupAllFiles() {
        return backupAllFiles;
    }

    public boolean isDeleteDestinationFiles() {
        return deleteDestinationFiles;
    }

    public Set<Path> getExpectedDestinationFiles() {
        return expectedDestinationFiles;
    }
}
