package com.dam.catalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;

/**
 * 密码加解密服务
 * <p>
 * 双通道加解密：
 * <ul>
 *   <li><b>传输加密</b>：RSA（服务启动生成密钥对，公钥通过接口下发前端，
 *       前端用 JSEncrypt 公钥加密密码后提交，后端私钥解密），保证 HTTP 明文传输时密码不可读</li>
 *   <li><b>存储加密</b>：AES（密码落库前加密存储，连接目标库时解密），避免数据库中直接保存明文密码</li>
 * </ul>
 *
 * @author DAM Platform
 */
@Service
@Slf4j
public class CryptoService {

    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /** 存储密文前缀，用于区分加密存储与旧明文数据 */
    private static final String STORAGE_PREFIX = "ENC:";

    /** 前端传输解密用 RSA 私钥 */
    private final PrivateKey rsaPrivateKey;
    /** 下发前端的 RSA 公钥（Base64 / X.509 编码） */
    private final String publicKeyBase64;
    /** 数据库存储加密用 AES 密钥 */
    private final SecretKeySpec aesKey;

    public CryptoService(@Value("${dam.security.crypto.secret:dam-platform-crypto-secret-2026}") String secret) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            this.rsaPrivateKey = kp.getPrivate();
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            // AES 密钥由配置 secret 的 SHA-256 前 16 字节派生
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.aesKey = new SecretKeySpec(Arrays.copyOf(hash, 16), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("密码加解密服务初始化失败", e);
        }
    }

    /**
     * 返回前端加密使用的 RSA 公钥（Base64 / X.509 编码）
     */
    public String getPublicKey() {
        return publicKeyBase64;
    }

    /**
     * RSA 解密前端传输的密码密文
     * <p>兼容旧客户端直接明文传输的场景：解密失败时原样返回</p>
     */
    public String decryptPassword(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("RSA 解密失败(可能为明文直传): {}", e.getMessage());
            return encrypted;
        }
    }

    /**
     * AES 加密后用于数据库存储
     */
    public String encryptStorage(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return STORAGE_PREFIX + Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            log.error("AES 加密失败", e);
            return plain;
        }
    }

    /**
     * AES 解密数据库存储的密码（兼容未加密的旧数据）
     */
    public String decryptStorage(String stored) {
        if (stored == null || !stored.startsWith(STORAGE_PREFIX)) {
            return stored;
        }
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(stored.substring(STORAGE_PREFIX.length())));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES 解密失败", e);
            return stored;
        }
    }
}
