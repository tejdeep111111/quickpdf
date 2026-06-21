module com.quickpdf {
    requires javafx.controls;
    requires javafx.graphics;
    requires java.desktop;
    requires org.apache.pdfbox;
    requires java.logging;
    requires com.github.kwhat.jnativehook;

    opens com.quickpdf to javafx.graphics, javafx.controls;
}
