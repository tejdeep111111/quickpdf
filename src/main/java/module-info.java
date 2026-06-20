module com.quickpdf {
    requires javafx.controls;
    requires javafx.graphics;
    requires java.desktop;
    requires org.apache.pdfbox;
    requires java.logging;

    opens com.quickpdf to javafx.graphics;
    //opens com.quickpdf.ui to javafx.graphics, javafx.controls;
}
