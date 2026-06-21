package com.quickpdf;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.logging.Logger;

//Sits between the UI and the conversion service, handling drag and drop events and passing the files to the conversion service for processing.
public class DragDropHandler {
    private static final Logger LOG = Logger.getLogger(DragDropHandler.class.getName());

    private final ConversionService conversionService;

    //Called with the PDF file after the conversion is complete. The UI can use this to display the PDF or perform other actions.
    private final Consumer<File> onSuccess;


    //"I don't know yet what to do with the PDF, but whoever creates me will tell me."
    public DragDropHandler(ConversionService conversionService, Consumer<File> onSuccess) {
        if(conversionService == null) {
            throw new IllegalArgumentException("ConversionService cannot be null");
        }
        if(onSuccess == null) {
            throw new IllegalArgumentException("onSuccess callback cannot be null");
        }
        this.conversionService = conversionService;
        this.onSuccess = onSuccess;
    }

    //Called by UI when a file is dropped onto the application. It checks if the file is supported.
    public boolean canHandle(Path path) {
        return conversionService.supports(path);
    }

    public void handle(File file) {
        if(file == null) {
            LOG.warning("Received a null file to handle.");
            return;
        }
        try {
            File pdfFile = conversionService.convert(file);
            LOG.info("Successfully converted file: " + file.getAbsolutePath() + " to PDF: " + pdfFile.getAbsolutePath());
            onSuccess.accept(pdfFile); //Tell the UI that the conversion was successful and provide the PDF file.
        } catch (IllegalArgumentException e) {
            LOG.warning("Invalid file: " + e.getMessage());
        } catch (IOException e) {
            LOG.severe("Conversion failed: " + e.getMessage());
        }
    }


}