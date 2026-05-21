package com.quickpdf;

import java.nio.file.Path;
import java.util.Objects;

public final class ConversionService {

    public boolean supports(Path file) {
        Objects.requireNonNull(file, "file");
        return true;
    }
}
