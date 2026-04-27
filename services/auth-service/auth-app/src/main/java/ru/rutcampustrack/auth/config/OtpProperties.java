package ru.rutcampustrack.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otp")
public record OtpProperties(
    int codeLength,
    int ttlSeconds,
    int maxAttempts,
    int attemptsWindowSeconds,
    int resendCooldownSeconds,
    /**
     * M16 G3 — лимит mismatch'ей на верификацию по коду (verifyOtpByCode)
     * с одного IP в окне {@link #verifyByCodeWindowSeconds}. Защита от
     * brute-force кодов: атакующий не знает telegramId, может перебирать
     * только сам 6-digit код в reverse-lookup endpoint'е /otp/verify-by-code.
     *
     * Default 20: при OTP TTL 120s + ~10 одновременных live OTP в Redis,
     * вероятность случайно угадать любой = 10/10^6 → ~1.7×10^-4 за 21
     * попытку. Cap далеко достаточный для legitimate users (typo, paste).
     */
    int verifyByCodeMissesPerWindow,
    int verifyByCodeWindowSeconds
) {}
