package com.example.ilearned;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

//Utility class to extract plain text from uploaded documents.

public class DocumentTextExtractor {

    private static final String TAG = "DocumentTextExtractor";

    //Extract text from a document URI.


    public static String extractText(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) mimeType = "";

        try {
            if (mimeType.equals("text/plain")) {
                return extractFromTextFile(context, uri);
            } else if (mimeType.equals("application/pdf")) {
                return extractFromPdf(context, uri);
            } else if (mimeType.contains("wordprocessingml") || mimeType.contains("msword")) {
                // Basic fallback – for full DOCX support add Apache POI
                return extractFromTextFile(context, uri);
            } else {
                // Try plain text as fallback
                return extractFromTextFile(context, uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Text extraction failed", e);
            return "";
        }
    }

    //  Plain text extraction
    private static String extractFromTextFile(Context context, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    //  PDF extraction using iText7
    private static String extractFromPdf(Context context, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            PdfReader reader      = new PdfReader(is);
            PdfDocument pdfDoc    = new PdfDocument(reader);
            int numberOfPages     = pdfDoc.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                String pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i));
                sb.append(pageText).append("\n");
            }
            pdfDoc.close();
        }
        return sb.toString().trim();
    }
}
