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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static java.util.concurrent.ForkJoinTask.invokeAll;
import java.util.concurrent.RecursiveAction;

public class BackupForkThread extends RecursiveAction
{

    private final WorkerThread parentThread;
    private final Path root;
    private final Path destinationRoot;
    private final boolean backupAllFiles;
    private final boolean deleteDestinationFiles;

    public BackupForkThread(WorkerThread parentThread, Path root, Path destinationRoot, boolean backupAllFiles, boolean deleteDestinationFiles)
    {
	this.parentThread = parentThread;
	this.root = root;
	this.destinationRoot = destinationRoot;
	this.backupAllFiles = backupAllFiles;
	this.deleteDestinationFiles = deleteDestinationFiles;
    }

    @Override
    protected void compute()
    {
	try (DirectoryStream<Path> ds = Files.newDirectoryStream(root))
	{
	    Files.createDirectories(makeDestinationPath(root));
	    Set<Path> files = new HashSet<>();
	    Set<Path> destinationFiles = new HashSet<>();
	    Set<Path> destinationDirectories = new HashSet<>();
	    List<RecursiveAction> directories = new ArrayList<>();
	    for (Path child : ds)
	    {
		if (Files.isRegularFile(child))
		{
		    files.add(child);
		} else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
		{
		    destinationDirectories.add(makeDestinationPath(child));
		    directories.add(new BackupForkThread(parentThread, child, destinationRoot, backupAllFiles, deleteDestinationFiles));
		}
	    }
	    Progress progress = new Progress(root, 0, files.size(), directories.size());
	    parentThread.publishProgress(progress);
	    for (Path child : files)
	    {
		Path destinationChild = makeDestinationPath(child);
		destinationFiles.add(destinationChild);
		DosFileAttributes attr = Files.readAttributes(child, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		if (backupAllFiles || attr.isArchive())
		{
		    Files.copy(child, destinationChild, StandardCopyOption.REPLACE_EXISTING);
		    Files.setAttribute(child, "dos:archive", false, LinkOption.NOFOLLOW_LINKS);
		}
		//Call it moved whether it actually was copied or not
		progress = progress.incrementFilesMoved();
		parentThread.publishProgress(progress);
	    }
	    if (deleteDestinationFiles)
	    {
		try (DirectoryStream<Path> destinationDs = Files.newDirectoryStream(makeDestinationPath(root)))
		{
		    for (Path child : destinationDs)
		    {
			if (Files.isRegularFile(child) && !destinationFiles.contains(child))
			{
			    Files.delete(child);
			} else if (Files.isDirectory(child) && !destinationDirectories.contains(child))
			{
			    directories.add(new DeleteForkThread(child));
			}
		    }
		} catch (IOException e)
		{
		    System.err.println("Exception occurred at " + root + " while deleting destination files. " + e.getMessage());
		}
	    }
	    if (!directories.isEmpty())
	    {
		invokeAll(directories);
	    }

	} catch (IOException e)
	{
	    System.err.println("Exception occurred at " + root + ". " + e.getMessage());
	}
    }

    private Path makeDestinationPath(Path sourcePath)
    {
	Path relativeSource = sourcePath.subpath(0, sourcePath.getNameCount());
	return Paths.get(destinationRoot.toString(), relativeSource.toString());
    }
}
