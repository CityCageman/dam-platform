package com.dam.access.integration.access.service;

import com.dam.platform.common.exception.BusinessException;
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
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Base64;

/**
 * 数据接入密码加解密服务
 * <p>
 * 与 catalog 的 {@code CryptoService} 使用完全一致的算法与密钥派生规则（不依赖 catalog 类）：
 * <ul>
 *   <li><b>传输加密</b>：RSA{@code /ECB/PKCS1Padding}，服务启动生成 2048 位密钥对，
 *       公钥经 {@code /api/access/public-key} 下发前端；私钥解密前端提交的密码。</li>
 *   <li><b>存储加密</b>：AES{@code /ECB/PKCS5Padding}，密钥为
 *       {@code dam.security.crypto.secret} 的 SHA-256 前 16 字节，密文以 {@code ENC:} 前缀落库。</li>
 *   <li><b>历史兼容</b>：读取时不以 {@code ENC:} 开头的存量密码按明文处理；
 *       传输层非 RSA 密文块按历史明文请求兼容处理。</li>
 * </ul>
 *
 * @author DAM Platform
 */
@Service
@Slf4j
public class AccessCryptoService {

    /** RSA 变换算法 */
    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    /** AES 变换算法 */
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /** 存储密文前缀，用于区分 AES 密文与历史明文 */
    private static final String STORAGE_PREFIX = "ENC:";

    /** 前端传输解密用 RSA 私钥 */
    private final PrivateKey rsaPrivateKey;

    /** 下发前端的 RSA 公钥（Base64 / X.509 编码） */
    private final String publicKeyBase64;

    /** 数据库存储加密用 AES 密钥 */
    private final SecretKeySpec aesKey;

    /** RSA 密文字节长度（2048 位 = 256 字节），用于区分 RSA 密文与历史明文 */
    private final int rsaBlockSize;

    /**
     * 初始化 RSA 密钥对与 AES 密钥
     *
     * @param secret AES 密钥派生配置，与 catalog 保持相同默认值以兼容存量密文
     */
    public AccessCryptoService(
            @Value("${dam.security.crypto.secret:dam-platform-crypto-secret-2026}") String secret) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            this.rsaPrivateKey = kp.getPrivate();
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.aesKey = new SecretKeySpec(Arrays.copyOf(hash, 16), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("数据接入密码加解密服务初始化失败", e);
        }
        this.rsaBlockSize = ((RSAPrivateKey) this.rsaPrivateKey).getModulus().bitLength() / 8;
    }

    /**
     * 返回前端加密使用的 RSA 公钥（Base64 / X.509 编码）
     *
     * @return 公钥字符串
     */
    public String getPublicKey() {
        return publicKeyBase64;
    }

    /**
     * RSA 解密前端传输的密码密文
     * <p>兼容历史前端/脚本直接提交明文密码：仅当入参不是 2048 位 RSA 密文块时按明文原样返回；
     * 若是 RSA 密文块但解密失败（典型为服务重启后前端仍持有旧公钥），明确报错而不是把密文当明文落库。
     *
     * @param encrypted RSA 密文或历史明文
     * @return 明文密码；入参为空时返回空串
     */
    public String decryptTransport(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return "";
        }
        byte[] cipherBytes;
        try {
            // 仅用于解码尝试，失败时仍按原始入参作为明文
            cipherBytes = Base64.getDecoder().decode(encrypted.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            return encrypted;
        }
        if (cipherBytes.length != rsaBlockSize) {
            // 非 RSA 块（如普通明文密码），按历史明文兼容
            return encrypted;
        }
        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 不记录密码/密文内容
            log.warn("RSA 解密失败(可能服务已重启，前端仍使用旧公钥): {}", e.getMessage());
            throw new BusinessException("密码解密失败，请刷新页面后重试；服务重启后旧的公钥会失效");
        }
    }

    /**
     * AES 加密后用于数据库存储
     *
     * @param plain 明文密码
     * @return {@code ENC:} 前缀密文；空密码原样返回
     */
    public String encryptStorage(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return STORAGE_PREFIX + Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            // 加密失败时绝不降级保存明文
            log.error("数据源密码 AES 加密失败", e);
            throw new BusinessException("数据源密码加密失败，请稍后重试");
        }
    }

    /**
     * AES 解密数据库存储的密码（兼容未加密的历史明文）
     *
     * @param stored 数据库密码字段
     * @return 明文密码；历史明文原样返回
     */
    public String decryptStorage(String stored) {
        if (stored == null || !stored.startsWith(STORAGE_PREFIX)) {
            return stored;
        }
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            byte[] plain = cipher.doFinal(
                    Base64.getDecoder().decode(stored.substring(STORAGE_PREFIX.length())));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 不返回密文冒充密码，也不记录密文内容
            log.error("数据源密码 AES 解密失败: {}", e.getClass().getSimpleName());
            throw new BusinessException("数据源密码解密失败，请重新保存数据源密码");
        }
    }
}
