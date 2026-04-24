package cn.keking.utils;

import cn.keking.config.ConfigConstants;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES加密解密工具类（目前AES比DES和DES3更安全，速度更快，对称加密一般采用AES）
 */
public class AESUtil {
    private static final String aesKey = ConfigConstants.getaesKey();

    /**
     * AES解密
     */
    public static String AesDecrypt(String url) {
        if (!aesKey(aesKey)) {
            return null;
        }
        try {
            byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] encrypted1 = Base64.getDecoder().decode(url);//先用base64解密
            byte[] original = cipher.doFinal(encrypted1);
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (e.getMessage().contains("Given final block not properly padded. Such issues can arise if a bad key is used during decryption")) {
                return "Keyerror";
            }else if (e.getMessage().contains("Input byte array has incorrect ending byte")) {
                return "byteerror";
            }else if (e.getMessage().contains("Illegal base64 character")) {
                return "base64error";
            }else if (e.getMessage().contains("Input length must be multiple of 16 when decrypting with padded cipher")) {
                return "byteerror";
            }else {
                System.out.println("ace错误:"+e);
                return null;
            }
        }
    }

    /**
     * AES加密
     */
    public static String aesEncrypt(String url) {
        if (!aesKey(aesKey)) {
            return null;
        }
        try {
            byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");//"算法/模式/补码方式"
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            byte[] encrypted = cipher.doFinal(url.getBytes(StandardCharsets.UTF_8));
            return new String(Base64.getEncoder().encode(encrypted));//此处使用BASE64做转码功能，同时能起到2次加密的作用。
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean aesKey(String aesKey) {
        if (aesKey == null) {
            System.out.print("Key为空null");
            return false;
        }
        // 判断Key是否为16位
        if (aesKey.length() != 16) {
            System.out.print("Key长度不是16位");
            return false;
        }
        return true;
    }
}
