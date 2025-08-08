package com.hmdp.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT 工具类，负责生成、解析、校验 token。
 */
@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private Key signKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    // 初始化签名 key（只做一次）
    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.signKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // 获取当前时间
    private Date now() {
        return new Date(System.currentTimeMillis());
    }

    // 获取 token 过期时间
    private Date getExpirationDate() {
        return new Date(System.currentTimeMillis() + jwtProperties.getExpiration());
    }

    // ==========================
    //       生成 Token
    // ==========================

    /**
     * 生成只包含用户名的 Token
     */
    public String generateToken(String userId) {
        return generateToken(Map.of(), userId);
    }


    /**
     * 生成包含额外 claims 的 Token
     */
    public String generateToken(Map<String, Object> extraClaims, String userId) {
        return Jwts.builder()
                .setClaims(extraClaims)              // 自定义字段，如 username
                .setSubject(userId)                // 标准字段：用户id
                .setIssuedAt(now())                  // 签发时间
                .setExpiration(getExpirationDate())  // 过期时间
                .signWith(signKey, SignatureAlgorithm.HS256) // 签名
                .compact();
    }

    // ==========================
    //       提取 Claims
    // ==========================

    /**
     * 从 token 中提取所有 claims（不校验是否过期）
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 提取任意字段
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 提取用户Id（Subject）
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 提取过期时间
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * 提取用户 ID（从自定义 Claim 中）
     */
    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("username", String.class); // 假设你生成 token 时放了 userId
    }

    // ==========================
    //       校验 Token
    // ==========================

    /**
     * Token 是否过期
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(now());
    }

    /**
     * Token 是否有效（用户名一致，未过期）
     */
    public boolean isTokenValid(String token, String username) {
        return extractUsername(token).equals(username) && !isTokenExpired(token);
    }
}

