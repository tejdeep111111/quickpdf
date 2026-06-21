package com.quickpdf;


//When QuickPDF converts a JPG, it writes a temporary PDF to the system's temp folder. TempFileManager makes sure those files don't pile up forever

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TempFileManager {
    //Logger for logging messages
    private static final Logger LOG = Logger.getLogger(TempFileManager.class.getName());

    //Singleton pattern to ensure only one instance of TempFileManager exists
    private static final TempFileManager INSTANCE = new TempFileManager();

    private final List<File> trackedFiles = new ArrayList<>();

    //prevents registering the shutdown hook multiple times
    private boolean hookRegistered = false;

    //Private constructor to prevent instantiation from outside the class
    private TempFileManager() {}

    //Singleton pattern to get the single instance of TempFileManager
    public static TempFileManager getInstance() {
        return INSTANCE;
    }

    //Method to register a temporary file for tracking
    public synchronized void track(File file) {
        if(file == null) {
            LOG.warning("Attempted to register a null file for tracking.");
            return;
        }
        trackedFiles.add(file);
        registerShutdownHook();
        LOG.info("Registered temporary file for tracking: " + file.getAbsolutePath());
    }

    //Called after the user drags the PDF out. The 500ms window lets the OS finish reading the file before we delete it. Daemon thread so it doesn't block JVM shutdown.
    public void deleteWithDelay(File file) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                LOG.warning("Thread interrupted while waiting to delete file: " + file.getAbsolutePath());
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
            deleteFile(file);
            synchronized (this) {
                trackedFiles.remove(file);
            }
        }, "QuickPDF-TempFileDeletionThread");
        t.setDaemon(true);
        t.start();
    }

    //Called by the shutdown hook and in tests for cleanup. clear() after delete so a second call is a no-op.
    public synchronized void deleteAll() {
        trackedFiles.forEach(this::deleteFile);
        trackedFiles.clear();
    }

    //Null + exists check = safe to call on already-deleted files. Logs a warning if deletion fails (e.g. file locked by the target app).
    private void deleteFile(File file) {
        if (file != null && file.exists()) {
            if (file.delete()) {
                LOG.info("Deleted temporary file: " + file.getAbsolutePath());
            } else {
                LOG.warning("Failed to delete temporary file: " + file.getAbsolutePath());
            }
        }
    }

    //"Hey JVM — right before you die, please run this thread for me."

    //JVM calls this thread when the process exits (normal close, System.exit(), or Ctrl+C). The hookRegistered flag prevents adding multiple hooks if track() is called many times.
    private synchronized void registerShutdownHook() {
        if (!hookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::deleteAll, "QuickPDF-TempFileShutdownHook"));
            hookRegistered = true;
            LOG.info("Registered shutdown hook for temporary file cleanup.");
        }
    }







}