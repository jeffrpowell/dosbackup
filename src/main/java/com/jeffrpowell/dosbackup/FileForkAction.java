package com.jeffrpowell.dosbackup;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static java.util.concurrent.ForkJoinTask.invokeAll;
import java.util.concurrent.RecursiveAction;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FileForkAction<C> extends RecursiveAction{

    private final BiConsumer<Path, C> fileAction;
    private final BiConsumer<Path, C> directoryAction;
    private final C configContainer;
    private final BiConsumer<Path, Exception> exceptionHandler;
    private final Consumer<Progress> progressHandler;
    private final Path workingDir;

    public FileForkAction(Path workingDir, BiConsumer<Path, C> fileAction, BiConsumer<Path, C> directoryAction, C configContainer, BiConsumer<Path, Exception> exceptionHandler, Consumer<Progress> progressHandler) {
        if (workingDir == null || fileAction == null) {
            throw new IllegalArgumentException("workingDir and action arguments are required");
        }
        this.workingDir = workingDir;
        this.fileAction = fileAction;
        this.directoryAction = directoryAction;
        this.configContainer = configContainer;
        this.exceptionHandler = exceptionHandler;
        this.progressHandler = progressHandler;
    }

    @Override
    protected void compute() {
        try ( DirectoryStream<Path> ds = Files.newDirectoryStream(workingDir)) {
            Set<Path> files = new HashSet<>();
            List<FileForkAction<C>> directories = new ArrayList<>();
            for (Path child : ds) {
                if (Files.isRegularFile(child)) {
                    files.add(child);
                } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    directories.add(new FileForkAction(child, fileAction, directoryAction, configContainer, exceptionHandler, progressHandler));
                }
            }
            Progress progress = new Progress(workingDir, 0, files.size(), directories.size());
            publishProgress(progress);
            for (Path child : files) {
                fileAction.accept(child, configContainer);
                //Call it moved whether it actually was copied or not
                progress = progress.incrementFilesMoved();
                publishProgress(progress);
            }
            if (directoryAction != null) {
                directoryAction.accept(workingDir, configContainer);
            }
            if (!directories.isEmpty()) {
                invokeAll(directories);
            }
        } catch (IOException e) {
            if (exceptionHandler != null) {
                exceptionHandler.accept(workingDir, e);
            }
        }
    }
    
    private void publishProgress(Progress p) {
        if (progressHandler != null) {
            progressHandler.accept(p);
        }
    }
}
