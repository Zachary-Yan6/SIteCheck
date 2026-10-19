package com.zachary.sitecheck.entity;

/** Whether the photo file has reached S3 yet; stored as text in {@code photos.status}. */
public enum PhotoStatus {
    /** Known from the submitted inspection, file not uploaded yet. */
    PENDING,
    /** File confirmed in S3. */
    UPLOADED
}
