package com.cloudbees.plugins.deployer.sources;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;

public class FilePathValidator {

    private FilePathValidator() {
        // to hide the implicit public constructor
    }

    /**
     * Checks whether a given child path is a descendant of a given parent path using {@link File#getCanonicalFile}.
     *
     * If the child path does not exist, this method will canonicalize path elements such as {@code /../} and
     * {@code /./} before comparing it to the parent path, and it will not throw an exception. If the child path
     * does exist, symlinks will be resolved before checking whether the child is a descendant of the parent path.
     * @param child FilePath
     * @param parent FilePath
     * @return boolean value of whether child path is a descendant of parent path
     * @throws IllegalStateException when child or parent FilePath represent remote file
     * @throws IOException when {@link File#getCanonicalFile} throws
     */
    public static boolean isDescendant(FilePath child, FilePath parent) throws IOException {
        if (child.isRemote() || parent.isRemote()) {
            throw new IllegalStateException("Directory path '" + parent + "' is not located on the controller");
        }
        return new File(child.getRemote()).getCanonicalFile().toPath().startsWith(new File(parent.getRemote()).getCanonicalPath());
    }
}
