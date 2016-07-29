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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import static java.util.concurrent.ForkJoinTask.invokeAll;
import java.util.concurrent.RecursiveAction;
import javax.swing.SwingWorker;

public class WorkerThread extends SwingWorker<Void, Progress> {

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
	protected Void doInBackground() throws Exception{
		for (Path path : paths){
			if (isCancelled())
			{
				forkJoinPool.shutdownNow();
				break;
			}
			else
			{
				BackupForkThread thread = new BackupForkThread(this, path, destination, backupAllFiles);
				forkJoinPool.invoke(thread);
			}
		}
		return null;
	}

	@Override
	protected void process(List<Progress> progresses)
	{
		for (Progress progress : progresses){
			if (!progressMap.containsKey(progress.getDirectory()))
			{
				progressMap.put(progress.getDirectory(), progress);
			}
			else
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
		for (Progress progress : progressMap.values()){
			found += progress.getFilesFound();
			moved += progress.getFilesMoved();
		}
		observer.updateProgress(moved, found);
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

	public class BackupForkThread extends RecursiveAction{
		private final WorkerThread parentThread;
		private final Path root;
		private final Path destinationRoot;
		private final boolean backupAllFiles;

		public BackupForkThread(WorkerThread parentThread, Path root, Path destinationRoot, boolean backupAllFiles){
			this.parentThread = parentThread;
			this.root = root;
			this.destinationRoot = destinationRoot;
			this.backupAllFiles = backupAllFiles;
		}

		@Override
		protected void compute(){
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
				Files.createDirectories(makeDestinationPath(root));
				List<Path> files = new ArrayList<>();
				List<BackupForkThread> directories = new ArrayList<>();
				for (Path child : ds) {
					if (Files.isRegularFile(child)) {
						files.add(child);
					}
					else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)){
						directories.add(new BackupForkThread(parentThread, child, destinationRoot, backupAllFiles));
					}
				}
				if (!directories.isEmpty()){
					invokeAll(directories);
				}
				Progress progress = new Progress(root, 0, files.size());
				parentThread.publish(progress);
				for (Path child : files){
					DosFileAttributes attr = Files.readAttributes(child, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
					if (backupAllFiles || attr.isArchive()){
						Files.copy(child, makeDestinationPath(child), StandardCopyOption.REPLACE_EXISTING);
						Files.setAttribute(child, "dos:archive", false, LinkOption.NOFOLLOW_LINKS);
					}
					//Call it moved whether it actually was copied or not
					progress = progress.incrementFilesMoved();
					parentThread.publish(progress);
				}
			}
			catch (IOException e){
				System.err.println("Exception occurred at "+root);
			}
		}

		private Path makeDestinationPath(Path sourcePath){
			Path relativeSource = sourcePath.subpath(0, sourcePath.getNameCount());
			return Paths.get(destinationRoot.toString(), relativeSource.toString());
		}
	}
}
