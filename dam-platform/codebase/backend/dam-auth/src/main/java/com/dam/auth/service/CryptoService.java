package com.dam.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

/**
 * 密码加解密服务（认证模块）
 * <p>
 * 前端登录前使用 RSA 公钥加密密码，后端私钥解密后再进行 BCrypt 校验，
 * 保证 HTTP 明文传输时登录密码不可读。
 *
 * @author DAM Platform
 */
@Service
@Slf4j
public class CryptoService {

    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    /** 前端传输解密用 RSA 私钥 */
    private final PrivateKey rsaPrivateKey;
    /** 下发前端的 RSA 公钥（Base64 / X.509 编码） */
    private final String publicKeyBase64;

    public CryptoService(@Value("${dam.security.crypto.secret:dam-platform-crypto-secret-2026}") String secret) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            this.rsaPrivateKey = kp.getPrivate();
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
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
}
