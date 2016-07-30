package com.jeffrpowell.dosbackup;

import java.nio.file.Path;

public class Progress implements Comparable<Progress>{
	private final Path directory;
	private final int filesMoved;
	private final int filesFound;
	private final int directoriesFound;

	public Progress(Path directory, int filesMoved, int filesFound, int directoriesFound){
		this.directory = directory;
		this.filesMoved = filesMoved;
		this.filesFound = filesFound;
		this.directoriesFound = directoriesFound;
	}

	public Path getDirectory(){
		return directory;
	}

	public int getFilesMoved(){
		return filesMoved;
	}

	public int getFilesFound(){
		return filesFound;
	}
	
	public int getDirectoriesFound(){
		return directoriesFound;
	}
	
	public Progress incrementFilesMoved(){
		return new Progress(directory, filesMoved+1, filesFound, directoriesFound);
	}

	@Override
	public int compareTo(Progress o){
		int comparison = directory.compareTo(o.getDirectory());
		if (comparison != 0)
		{
			return comparison;
		}
		comparison = Integer.compare(filesMoved, o.getFilesMoved());
		if (comparison != 0)
		{
			return comparison;
		}
		return Integer.compare(filesFound, o.getFilesFound());
	}
	
}
