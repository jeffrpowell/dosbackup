package com.jeffrpowell.dosbackup;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import javax.swing.SwingWorker;

public class WorkerThread extends SwingWorker<Void, Progress>
{

    private final Set<Path> paths;
    private final Path destination;
    private final boolean backupAllFiles;
    private final BackupObserver observer;
    private final ForkJoinPool forkJoinPool;
    private final Map<Path, Progress> progressMap;

    public WorkerThread(Set<Path> paths, Path destination, boolean backupAllFiles, BackupObserver observer)
    {
	this.paths = paths;
	this.destination = destination;
	this.backupAllFiles = backupAllFiles;
	this.observer = observer;
	this.forkJoinPool = new ForkJoinPool();
	this.progressMap = new HashMap<>();
    }

    @Override
    protected Void doInBackground() throws Exception
    {
	for (Path path : paths)
	{
	    if (isCancelled())
	    {
		forkJoinPool.shutdownNow();
		break;
	    } else
	    {
		BackupForkThread thread = new BackupForkThread(this, path, destination, backupAllFiles);
		forkJoinPool.invoke(thread);
	    }
	}
	return null;
    }
    
    public void publishProgress(Progress progress)
    {
	publish(progress);
    }

    @Override
    protected void process(List<Progress> progresses)
    {
	for (Progress progress : progresses)
	{
	    if (!progressMap.containsKey(progress.getDirectory()))
	    {
		progressMap.put(progress.getDirectory(), progress);
	    } else
	    {
		Progress oldProgress = progressMap.get(progress.getDirectory());
		int comparison = oldProgress.compareTo(progress);
		if (comparison < 0)
		{
		    progressMap.put(progress.getDirectory(), progress);
		}
	    }
	}
	int found = 0;
	int moved = 0;
	int directoriesLeft = paths.size();
	for (Progress progress : progressMap.values())
	{
	    found += progress.getFilesFound();
	    moved += progress.getFilesMoved();
	    directoriesLeft += progress.getDirectoriesFound();
	}
	directoriesLeft -= progressMap.size();
	observer.updateProgress(moved, found, directoriesLeft);
	if (isCancelled())
	{
	    forkJoinPool.shutdownNow();
	    observer.done();
	}
    }

    @Override
    protected void done()
    {
	observer.done();
    }
    
}
