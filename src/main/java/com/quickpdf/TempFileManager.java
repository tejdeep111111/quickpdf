package com.quickpdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TempFileManager {

    public Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(prefix, suffix);
    }
}
