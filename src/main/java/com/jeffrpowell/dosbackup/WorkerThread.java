package com.jeffrpowell.dosbackup;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import javax.swing.SwingWorker;

public class WorkerThread extends SwingWorker<Void, Progress> {

    private final Set<Path> paths;
    private final BackupConfig config;
    private final BackupObserver observer;
    private final ForkJoinPool forkJoinPool;
    private final Map<Path, Progress> progressMap;
	private final List<String> failedPaths;

    public WorkerThread(Set<Path> paths, Path destination, boolean backupAllFiles, boolean deleteDestinationFiles, BackupObserver observer) {
        this.paths = paths;
        this.config = new BackupConfig(this, destination, backupAllFiles, deleteDestinationFiles);
        this.observer = observer;
        this.forkJoinPool = new ForkJoinPool();
        this.progressMap = new HashMap<>();
		this.failedPaths = new ArrayList<>();
    }

    @Override
    protected Void doInBackground() throws Exception {
        for (Path path : paths) {
            if (isCancelled()) {
                forkJoinPool.shutdownNow();
                break;
            } else {
                FileForkAction thread = new FileForkAction<>(path, this::copyFile, this::deleteDestinationFiles, config, this::logException, this::publish);
                forkJoinPool.invoke(thread);
            }
        }
        return null;
    }
    
    private void logException(Path p, Exception e) {
		logException(p, "Exception occurred while performing an action on " + p, e);
    }
    
    private void logException(Path p, String message, Exception e) {
        System.err.println(message);
		failedPaths.add(p.toString());
		e.printStackTrace(System.err);
    }

    private void copyFile(Path file, BackupConfig config) {
        try {
            Path destinationChild = makeDestinationPath(file);
            config.getExpectedDestinationFiles().add(destinationChild);
            config.getExpectedDestinationFiles().add(destinationChild.getParent());
            DosFileAttributes attr = Files.readAttributes(file, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (config.isBackupAllFiles() || attr.isArchive()) {
				ensureDirectoriesAreCreated(file);
                Files.copy(file, destinationChild, StandardCopyOption.REPLACE_EXISTING);
                Files.setAttribute(file, "dos:archive", false, LinkOption.NOFOLLOW_LINKS);
            }
        } catch (IOException ex) {
			logException(file, "Exception occurred while copying " + file, ex);
        }
    }
	
	private void ensureDirectoriesAreCreated(Path targetFile) {
		Iterator<Path> i = targetFile.iterator();
		Path lastNode = null;
		while(i.hasNext()) {
			Path p = i.next();
			if (lastNode != null) {
				p = lastNode.resolve(p);
			}
			if (!p.getFileName().equals(targetFile.getFileName())) {
				ensureDirectoryIsCreated(p);
				lastNode = p;
			}
		}
	}
	
	private void ensureDirectoryIsCreated(Path p) {
		Path destinationDir = makeDestinationPath(p);
		if (!Files.exists(destinationDir)) {
			try
			{
				Files.createDirectory(destinationDir);
			} catch (IOException ex)
			{
				logException(p, "Exception while making directory " + p, ex);
			}
		}
	}

    private void deleteDestinationFiles(Path dir, BackupConfig config) {
        if (!config.isDeleteDestinationFiles()) {
            return;
        }
        try ( DirectoryStream<Path> destinationDs = Files.newDirectoryStream(makeDestinationPath(dir))) {
            for (Path child : destinationDs) {
                if (config.getExpectedDestinationFiles().contains(child)) {
                    continue;
                }
                if (Files.isRegularFile(child)) {
                    deleteFile(child, config);
                } else if (Files.isDirectory(child)) {
                    //recursively delete everything under this directory
                    forkJoinPool.invoke(
                        new FileForkAction<>(child, this::deleteFile, this::deleteFile, config, null, null)
                    );
                }
            }
        } catch (IOException e) {
            logException(dir, "Exception occurred at " + dir + " while closing directory stream.", e);
        }
    }
    
    private void deleteFile(Path file, BackupConfig c) {
        try {
            Files.delete(file);
        } catch (IOException ex) {
			logException(file, "Exception occurred while deleting " + file, ex);
        }
    }

    private Path makeDestinationPath(Path sourcePath) {
        Path relativeSource = sourcePath.subpath(0, sourcePath.getNameCount());
        return Paths.get(config.getDestinationRoot().toString(), relativeSource.toString());
    }
    
    @Override
    protected void process(List<Progress> progresses) {
        for (Progress progress : progresses) {
            if (!progressMap.containsKey(progress.getDirectory())) {
                progressMap.put(progress.getDirectory(), progress);
            } else {
                Progress oldProgress = progressMap.get(progress.getDirectory());
                int comparison = oldProgress.compareTo(progress);
                if (comparison < 0) {
                    progressMap.put(progress.getDirectory(), progress);
                }
            }
        }
        int found = 0;
        int moved = 0;
        int directoriesLeft = paths.size();
        for (Progress progress : progressMap.values()) {
            found += progress.getFilesFound();
            moved += progress.getFilesMoved();
            directoriesLeft += progress.getDirectoriesFound();
        }
        directoriesLeft -= progressMap.size();
        observer.updateProgress(moved, found, directoriesLeft);
        if (isCancelled()) {
            forkJoinPool.shutdownNow();
            observer.done(true, failedPaths);
        }
    }

    @Override
    protected void done() {
        observer.done(false, failedPaths);
		if (!forkJoinPool.isShutdown()) {
			forkJoinPool.shutdown();
		}
    }

}
