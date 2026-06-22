package com.projectflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.projectflow.support.AppException;

@Service
public class ProjectZipUploadGuard {
    private final long maxZipBytes;

    public ProjectZipUploadGuard(@Value("${projectflow.upload.max-zip-bytes:67108864}") long maxZipBytes) {
        this.maxZipBytes = maxZipBytes;
    }

    public void assertUploadBudget(long uploadedBytes) {
        if (uploadedBytes > maxZipBytes) {
            throw tooLarge();
        }
    }

    public long assertReadBudget(long currentBytes, long additionalBytes) {
        long next = currentBytes + Math.max(0, additionalBytes);
        if (next > maxZipBytes) {
            throw tooLarge();
        }
        return next;
    }

    private AppException tooLarge() {
        return new AppException(
            "ZIP_TOO_LARGE",
            "Project zip is too large. Remove dependencies, build outputs, logs, and binary assets before uploading.",
            HttpStatus.PAYLOAD_TOO_LARGE
        );
    }
}
