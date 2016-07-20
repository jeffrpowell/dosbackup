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
import java.util.List;
import java.util.concurrent.RecursiveAction;

public class BackupForkThread extends RecursiveAction{

	private final Path root;
	private final Path destinationRoot;
	private final boolean backupAllFiles;

	public BackupForkThread(Path root, Path destinationRoot, boolean backupAllFiles){
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
					directories.add(new BackupForkThread(child, destinationRoot, backupAllFiles));
				}
			}
			if (!directories.isEmpty()){
				invokeAll(directories);
			}
			for (Path child : files){
				DosFileAttributes attr = Files.readAttributes(child, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				if (backupAllFiles || attr.isArchive()){
					Files.copy(child, makeDestinationPath(child), StandardCopyOption.REPLACE_EXISTING);
					Files.setAttribute(child, "dos:archive", false, LinkOption.NOFOLLOW_LINKS);
				}
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
