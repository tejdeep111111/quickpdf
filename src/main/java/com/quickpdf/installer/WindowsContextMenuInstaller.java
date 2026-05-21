package com.quickpdf.installer;

public final class WindowsContextMenuInstaller {

    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
