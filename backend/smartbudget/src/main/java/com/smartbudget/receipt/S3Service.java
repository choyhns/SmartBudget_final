package com.smartbudget.receipt;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/**
 * AWS S3 업로드 및 Presigned URL 생성 서비스.
 * 영수증 이미지를 S3에 업로드하고, 비공개 버킷에서 조회용 Presigned URL을 발급한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "true")
public class S3Service {

    @Value("${aws.s3.bucket:}")
    private String bucket;

    @Value("${aws.region:ap-northeast-2}")
    private String region;

    private S3Client s3Client;
    private S3Presigner presigner;

    private S3Client getS3Client() {
        if (s3Client == null) {
            s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        }
        return s3Client;
    }

    private S3Presigner getPresigner() {
        if (presigner == null) {
            presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        }
        return presigner;
    }

    /**
     * 영수증 이미지를 user_id/연도/월/파일명 구조로 S3에 업로드.
     * 프론트에서 사용자가 확정한 날짜(transactionDate)를 기준으로 폴더를 만든다.
     *
     * @param userId       사용자 ID (JWT에서 추출한 값)
     * @param transactionDate 프론트에서 입력한 거래 날짜 (폴더 경로용)
     * @return S3 객체 키 (url_path에 저장할 값)
     */
    public String uploadReceipt(String userId, LocalDate transactionDate, byte[] bytes,
                                String originalFilename, String contentType) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("aws.s3.bucket is not set");
        }
        String safeFilename = originalFilename != null && !originalFilename.isBlank()
            ? originalFilename
            : "receipt.jpg";
        // 파일명 중복 방지
        String objectKey = "receipts/" + userId + "/" + transactionDate.getYear() + "/"
            + transactionDate.getMonthValue() + "/"
            + System.currentTimeMillis() + "_" + safeFilename;
        getS3Client().putObject(PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(contentType != null ? contentType : "image/jpeg")
            .build(), software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes));
        log.debug("S3 uploadReceipt success: bucket={}, key={}", bucket, objectKey);
        return objectKey;
    }

    /**
     * S3에 파일 업로드 후 객체 키 반환 (url_path에 저장할 값).
     */
    public String upload(String objectKey, byte[] bytes, String contentType) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("aws.s3.bucket is not set");
        }
        getS3Client().putObject(PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(contentType != null ? contentType : "image/jpeg")
            .build(), software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes));
        log.debug("S3 upload success: bucket={}, key={}", bucket, objectKey);
        return objectKey;
    }

    /**
     * Presigned URL 생성 (기본 60분 유효).
     */
    public String getPresignedUrl(String objectKey, int expirationMinutes) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("aws.s3.bucket is not set");
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(expirationMinutes))
            .getObjectRequest(getObjectRequest)
            .build();
        URL url = getPresigner().presignGetObject(presignRequest).url();
        return url.toString();
    }

}
