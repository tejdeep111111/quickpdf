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
import java.util.UUID;
import java.util.logging.Logger;

public class ConversionService {

    private static final Logger LOG = Logger.getLogger(ConversionService.class.getName());

    public boolean supports(Path path) {
        if(path==null) {
            return false;
        }
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg");
    }

    private File convert(File jpgFile) throws IOException {

        //STEP 1: Validate the input file
        if(jpgFile==null || !jpgFile.exists() || !supports(jpgFile.toPath())) {
            LOG.warning("Invalid JPG file provided for conversion: " + (jpgFile != null ? jpgFile.getAbsolutePath() : "null"));
            return null;
        }

        //STEP 2: Read the JPG file into memory
        BufferedImage image = ImageIO.read(jpgFile);
        if(image == null) {
            throw new IOException("Failed to read the JPG file: " + jpgFile.getAbsolutePath());
        }

        //Step 3: Convert pixel dimensions to points (1 point = 1/72 inch)
        float widthInPoints = image.getWidth() * 72f / 96f; // Assuming 96 DPI for JPG
        float heightInPoints = image.getHeight() * 72f / 96f;

        //STEP 4:Build the output file path
        String baseName = jpgFile.getName().replaceAll("(?i)\\.jpe?g$", "");
        File outputFile = new File(System.getProperty("java.io.tmpdir"), baseName + '_' + UUID.randomUUID() + ".pdf");

        //STEP 5: Create the PDF document using Apache PDFBox
        try(PDDocument doc = new PDDocument()) {
            //Create a new page with the same dimensions as the JPG image
            PDRectangle pageSize = new PDRectangle(widthInPoints, heightInPoints);
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);

            //Embed the JPG image into the PDF page
            PDImageXObject pdImage = PDImageXObject.createFromFile(jpgFile.getAbsolutePath(), doc);

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
}
