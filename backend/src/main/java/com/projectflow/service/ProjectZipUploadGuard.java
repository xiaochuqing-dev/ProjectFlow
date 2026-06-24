package com.projectflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.projectflow.support.AppException;

@Service
public class ProjectZipUploadGuard {
    private final long maxZipFileBytes;
    private final long maxZipReadBytes;

    public ProjectZipUploadGuard(
        @Value("${projectflow.upload.max-zip-file-bytes:536870912}") long maxZipFileBytes,
        @Value("${projectflow.upload.max-zip-read-bytes:67108864}") long maxZipReadBytes
    ) {
        this.maxZipFileBytes = maxZipFileBytes;
        this.maxZipReadBytes = maxZipReadBytes;
    }

    public void assertUploadBudget(long uploadedBytes) {
        if (uploadedBytes > maxZipFileBytes) {
            throw zipFileTooLarge();
        }
    }

    public long assertReadBudget(long currentBytes, long additionalBytes) {
        long next = currentBytes + Math.max(0, additionalBytes);
        if (next > maxZipReadBytes) {
            throw zipReadTooLarge();
        }
        return next;
    }

    private AppException zipFileTooLarge() {
        return new AppException(
            "ZIP_TOO_LARGE",
            "项目 zip 超过本地导入上限。请删除 node_modules、构建产物、日志和大型二进制资源后重新压缩。",
            HttpStatus.PAYLOAD_TOO_LARGE
        );
    }

    private AppException zipReadTooLarge() {
        return new AppException(
            "ZIP_TOO_LARGE",
            "项目 zip 中可分析文本过多。请删除依赖目录、构建产物、日志和大型生成文件后重新压缩。",
            HttpStatus.PAYLOAD_TOO_LARGE
        );
    }
}
