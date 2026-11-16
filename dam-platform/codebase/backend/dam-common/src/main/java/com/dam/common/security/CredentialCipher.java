package com.dam.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置凭据加解密组件。
 * <p>
 * 对集成/DEV/数据服务配置中的密码字段做 AES-GCM 加密后再落库，执行读取时解密。
 * 密文格式：{@code ENC1:base64(iv + ciphertext)}。为兼容历史数据，未带 {@code ENC1:}
 * 前缀的值按明文原样返回，下一次保存时再加密。
 * <p>
 * 生产必须通过 {@code DAM_CREDENTIAL_SECRET} 注入固定密钥；未配置时使用开发默认值，
 * 仅用于本地联调，不能用于生产。
 *
 * @author DAM Platform
 */
@Slf4j
@Component
public class CredentialCipher {

    /** 密文前缀，用于兼容历史明文配置 */
    public static final String PREFIX = "ENC1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * 构造凭据加解密组件。
     *
     * @param secret 密钥材料，长度建议 >= 32 字符；通过 SHA-256 派生 128 位 AES key
     */
    public CredentialCipher(
            @Value("${dam.security.credential-secret:${DAM_CREDENTIAL_SECRET:dam-dev-credential-secret-change-me}}")
            String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密组件初始化失败", e);
        }
    }

    /**
     * 加密明文；空值或已加密值原样返回。
     *
     * @param plain 明文
     * @return 密文或原值
     */
    public String encrypt(String plain) {
        if (!StringUtils.hasText(plain) || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    /**
     * 解密凭据；非密文或解密失败时按明文返回，避免历史数据无法执行。
     *
     * @param stored 密文或明文
     * @return 明文
     */
    public String decrypt(String stored) {
        if (!StringUtils.hasText(stored) || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (all.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度非法");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("凭据解密失败，按历史明文处理: {}", e.getMessage());
            return stored;
        }
    }
}
