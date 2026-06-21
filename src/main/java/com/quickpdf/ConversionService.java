package com.quickpdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

public class ConversionService {

    private static final Logger LOG = Logger.getLogger(ConversionService.class.getName());

    public boolean supports(Path path) {
        if(path==null) {
            return false;
        }
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png");
    }

    public File convert(File imageFile) throws IOException {

        //STEP 1: Validate the input file
        if(imageFile ==null || !imageFile.exists() || !supports(imageFile.toPath())) {
            throw new IllegalArgumentException("Invalid file provided for conversion: " + (imageFile != null ? imageFile.getAbsolutePath() : "null"));
        }

        //STEP 2: Read the JPG file into memory
        BufferedImage image = ImageIO.read(imageFile);
        if(image == null) {
            throw new IOException("Failed to read the JPG file: " + imageFile.getAbsolutePath());
        }

        //Step 3: Convert pixel dimensions to points (1 point = 1/72 inch)
        float widthInPoints = image.getWidth() * 72f / 96f; // Assuming 96 DPI for JPG
        float heightInPoints = image.getHeight() * 72f / 96f;

        //STEP 4:Build the output file path
        String rawName = imageFile.getName();
        String baseName = (rawName == null ? "quickpdf" : rawName)
                .replaceAll("(?i)\\.jpe?g$", "");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File outputFile = new File(System.getProperty("java.io.tmpdir"), baseName + "_" + timestamp + ".pdf");

        //STEP 5: Create the PDF document using Apache PDFBox
        try(PDDocument doc = new PDDocument()) {
            //Create a new page with the same dimensions as the JPG image
            PDRectangle pageSize = new PDRectangle(widthInPoints, heightInPoints);
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);

            //Embed the JPG image into the PDF page
            PDImageXObject pdImage = PDImageXObject.createFromFile(imageFile.getAbsolutePath(), doc);

            //Draw the image onto the PDF page
            try(PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.drawImage(pdImage, 0, 0, widthInPoints, heightInPoints);
            }

            doc.save(outputFile);
            LOG.info("PDF file created successfully: " + outputFile.getAbsolutePath());
        }

        //STEP 6: Register the output PDF file for deletion on JVM exit
        TempFileManager.getInstance().track(outputFile);

        return outputFile;
    }

    // Converts multiple JPG files into one merged PDF — one page per image
    public File convertAll(List<File> jpgFiles) throws IOException {
        if (jpgFiles == null || jpgFiles.isEmpty())
            throw new IllegalArgumentException("File list is null or empty");

        // Validate all files up front before touching PDFBox
        for (File f : jpgFiles) {
            if (f == null || !f.exists())
                throw new IllegalArgumentException("File does not exist: " + f);
            if (!supports(f.toPath()))
                throw new IllegalArgumentException("Not a jpg: " + (f.getName()));
        }

        // Output name: first filename + "and_N_more" suffix
        String rawName = jpgFiles.get(0).getName();
        String baseName = (rawName == null ? "quickpdf" : rawName)
                .replaceAll("(?i)\\.jpe?g$", "");
        String suffix = jpgFiles.size() > 1 ? "_and_" + (jpgFiles.size() - 1) + "_more" : "";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File outputFile = new File(System.getProperty("java.io.tmpdir"),
                baseName + suffix + "_" + timestamp + ".pdf");

        try (PDDocument doc = new PDDocument()) {
            for (File jpgFile : jpgFiles) {
                BufferedImage image = ImageIO.read(jpgFile);
                if (image == null)
                    throw new IOException("Could not read image: " + jpgFile.getName());

                float w = image.getWidth()  * 72f / 96f;
                float h = image.getHeight() * 72f / 96f;

                PDPage page = new PDPage(new PDRectangle(w, h));
                doc.addPage(page);

                PDImageXObject pdImage = PDImageXObject.createFromFile(jpgFile.getAbsolutePath(), doc);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.drawImage(pdImage, 0, 0, w, h);
                }
            }
            doc.save(outputFile);
            LOG.info("Merged PDF created: " + outputFile.getName() + " (" + jpgFiles.size() + " pages)");
        }

        TempFileManager.getInstance().track(outputFile);
        return outputFile;
    }
}
