package com.coperius.fullJarDecompiler;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.ZipOutputStream;

public class DecompileAllClassesAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(DecompileAllClassesAction.class);

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

        final VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (vf == null) {
            NotificationUtil.Warn(e.getProject(),"Decompilation Cancelled", "No file found in event");
            LOG.warn("DecompileAllClassesAction: No file selected");
            return;
        }

        var jarFile = getJarFileFor(vf);

        if (jarFile == null) {
            NotificationUtil.Warn(e.getProject(),"Decompilation Cancelled", "Not a jar file " + vf.getPath());
            LOG.warn("DecompileAllClassesAction: Not a jar file " + vf.getPath());
            return;
        }

        // Try to show legal notice
        try {
            LegalNotice.ShowOriginalNotice(e.getProject());
        } catch (Exception ex) {
            NotificationUtil.Error(e.getProject(),"Decompilation Cancelled", "Error showing legal notice: " + ex.getMessage());
            LOG.warn("Decompilation Cancelled: Error showing legal notice: " + ex.getMessage());
            return;
        }

        if (!LegalNotice.isAccepted()) {
            NotificationUtil.Warn(e.getProject(),"Decompilation Cancelled", "Legal notice not accepted");
            LOG.warn("Decompilation Cancelled: Legal notice not accepted");
            return;
        }

        // Create descriptor for directory selection
        FileChooserDescriptor directoryChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        directoryChooserDescriptor.setTitle("Select Directory to Save");

        // Show directory selection dialog
        VirtualFile selectedDir = FileChooser.chooseFile(directoryChooserDescriptor, e.getProject(), null);

        if (selectedDir == null) {
            LOG.warn("No directory selected");
            NotificationUtil.Warn(e.getProject(),"Decompilation Cancelled", "No directory selected");
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(e.getProject(), "Running background task") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // Code to run on a background thread with a progress indicator
                decompileWork(e.getProject(),jarFile, selectedDir);
                LOG.debug("Done");
            }
        });

    }

    private @Nullable VirtualFile getJarFileFor(VirtualFile vf) {
        if (vf == null) {
            return null;
        }

        if (!Objects.equals(vf.getExtension(), "jar")) {
            return null;
        }

        var fileSystem = vf.getFileSystem();
        if ((fileSystem instanceof JarFileSystem)) {
            var root = ((JarFileSystem) fileSystem).getRootByEntry(vf);

            if (root == null || !root.equals(vf)) {
                return null;
            }
            return vf;
        }

        var javaFS = VirtualFileManager.getInstance().getFileSystem(JarFileSystem.PROTOCOL);
        if (javaFS == null) {
            return null;
        }

        return javaFS.findFileByPath(vf.getPath() + "!/");

    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // show action only for jar files
        Presentation presentation = e.getPresentation();

        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);

        // hide action if not a jar file root
        if (getJarFileFor(vf) == null) {
            presentation.setVisible(false);
            return;
        }

        presentation.setVisible(true);

    }

    private void decompileWork(Project project, @NotNull VirtualFile root, @NotNull VirtualFile outputDir) {

        LOG.debug("Decompile jar file: " + root.getPath());

        // Create zip file and add all decompiled files with directory structure
        // and save to disk

        var entry = decompile(project, root);
        if (entry == null || entry.getChildren().isEmpty()) {
            NotificationUtil.Warn(project,"No classes found", "No classes found in jar " + root.getPath());
            LOG.warn("No classes found in jar " + root.getPath());
            return;
        }

        // create path from dirs and file names with path join
        var outputFilename = root.getNameWithoutExtension() + "-src-" + System.currentTimeMillis() + ".zip";
        var outputPath = Objects.requireNonNull(outputDir.getFileSystem().getNioPath(outputDir)).resolve(outputFilename).toString();

        ZipOutputStream zip;
        try {
            zip = new ZipOutputStream(new java.io.FileOutputStream(outputPath));
        } catch (FileNotFoundException ex) {
            NotificationUtil.Error(project,"Zip creation", "Error creating zip file: " + ex.getMessage());
            LOG.warn("Zip creation error: " + ex.getMessage());
            return;
        }

        try {
            for (Entry child : entry.getChildren()) {
                writeEntry(child, zip);
            }
            zip.close();
            NotificationUtil.Info(project,"Decompilation Done", "Decompilation done, saved to " + outputPath);
        } catch (IOException ex) {
            NotificationUtil.Error(project,"Zip creation", "Error: " + ex.getMessage());
            LOG.warn("Zip creation error: " + ex.getMessage());
        }
    }

    private void writeEntry(Entry entry, ZipOutputStream zip) throws IOException {
        var path = entry.getPath();
        if (entry.isDirectory()) {
            // create directory in zip
            if (entry.getChildren().isEmpty()) {
                return;
            }
            if (!path.endsWith("/")) {
                path += "/";
            }
            zip.putNextEntry(new java.util.zip.ZipEntry(path));
            for (Entry child : entry.getChildren()) {
                writeEntry(child, zip);
            }
            zip.closeEntry();
        } else {
            // write file to zip
            zip.putNextEntry(new java.util.zip.ZipEntry(path));
            zip.write(entry.getContent().getBytes());
            zip.closeEntry();
        }
    }

    private @Nullable Entry decompile(Project project, @NotNull VirtualFile vf) {

        // get path after .jar!/
        var path = vf.getPath().substring(vf.getPath().indexOf("!/") + 2);

        if (vf.isDirectory()) {
            var entry = new Entry(path, true);
            LOG.debug("Decompile dir: " + path);

            // Ignore "'VirtualFile.getChildren()' called from a recursive method" inspection
            // In jar files, the directory structure is flat, so we can ignore it
            for (VirtualFile child : vf.getChildren()) {
                var childEntry = decompile(project, child);
                if (childEntry != null) {
                    entry.addChild(childEntry);
                }
            }

            return entry.getChildren().isEmpty() ? null : entry;
        } else {
            // ignore non-class files
            if (!vf.getFileType().getName().equals("CLASS")) {
                return null;
            }
            // skip inner classes, they should be decompiled with their parent
            if (vf.getNameWithoutExtension().contains("$")) {
                return null;
            }

            LOG.debug("Decompile file: " + path);
            try {
                // replace .class with .java
                var entryPath = path.substring(0, path.length() - 6) + ".java";
                var entry = new Entry(entryPath, false);
                var contest = LoadTextUtil.loadText(vf);
                entry.setContent((String) contest);
                return entry;
            } catch (Exception ex) {
                LOG.warn("Decompilation Error: " + ex.getMessage());
                NotificationUtil.Warn(project,"Decompilation Error", "Error decompiling file " + path + ": "
                        + ex.getMessage());
                return null;
            }
        }
    }
}

