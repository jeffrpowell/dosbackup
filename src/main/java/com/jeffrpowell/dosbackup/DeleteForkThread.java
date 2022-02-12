package com.jeffrpowell.dosbackup;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

public class DeleteForkThread extends RecursiveAction {

    private final Path root;

    public DeleteForkThread(Path root) {
        this.root = root;
    }

    @Override
    protected void compute() {
        try ( DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            List<DeleteForkThread> directories = new ArrayList<>();
            for (Path child : ds) {
                if (Files.isRegularFile(child)) {
                    Files.delete(child);
                } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    directories.add(new DeleteForkThread(child));
                }
            }
            if (!directories.isEmpty()) {
                invokeAll(directories);
            }
            Files.delete(root);
        } catch (IOException ex) {
            System.err.println("Exception occurred at " + root + " while deleting destination files. " + ex.getMessage());
        }
    }

}
