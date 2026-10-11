package ru.rutcampustrack.documentrenderer.render;

/** The supplied bytes are not a DOCX package; retrying the converter cannot repair them. */
public class InvalidDocumentException extends DocumentConversionException {
    public InvalidDocumentException() {
        super("Input must be a valid DOCX document");
    }

    public InvalidDocumentException(Throwable cause) {
        super("Input must be a valid DOCX document", cause);
    }
}
