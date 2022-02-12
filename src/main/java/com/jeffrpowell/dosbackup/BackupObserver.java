package com.jeffrpowell.dosbackup;

import java.util.List;

public interface BackupObserver{
	public void updateProgress(int moved, int found, int directoriesLeft);
	public void done(boolean wasCancelled, List<String> failedFiles);
}
