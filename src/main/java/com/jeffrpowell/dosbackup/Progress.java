package com.jeffrpowell.dosbackup;

import java.nio.file.Path;

public class Progress implements Comparable<Progress>{
	private final Path directory;
	private final int filesMoved;
	private final int filesFound;

	public Progress(Path directory, int filesMoved, int filesFound){
		this.directory = directory;
		this.filesMoved = filesMoved;
		this.filesFound = filesFound;
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
	
	public Progress incrementFilesMoved(){
		return new Progress(directory, filesMoved+1, filesFound);
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
