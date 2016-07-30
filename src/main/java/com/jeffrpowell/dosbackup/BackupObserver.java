package com.jeffrpowell.dosbackup;

public interface BackupObserver{
	public void updateProgress(int moved, int found, int directoriesLeft);
	public void done();
}
