package com.scargo.app.util;

import java.math.BigDecimal;

/** 26.10.03 2차: ISO 6346 컨테이너 번호 검증 + 규격코드로 높이 추정 */
public final class ContainerNumbers {

    private ContainerNumbers() {
    }

    /** 대문자 4 + 숫자 7, 마지막 숫자는 체크디지트 */
    public static boolean isValid(String no) {
        if (no == null || !no.matches("^[A-Z]{4}[0-9]{7}$")) {
            return false;
        }
        return checkDigit(no.substring(0, 10)) == no.charAt(10) - '0';
    }

    public static int checkDigit(String first10) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char ch = first10.charAt(i);
            int v = Character.isLetter(ch) ? letterValue(ch) : ch - '0';
            sum += v * (1 << i);
        }
        return sum % 11 % 10;
    }

    // A=10 부터 시작하되 11의 배수(11, 22, 33)는 건너뜀
    private static int letterValue(char c) {
        int v = 10;
        for (char x = 'A'; x < c; x++) {
            v++;
            if (v % 11 == 0) v++;
        }
        return v;
    }

    public static String normalize(String no) {
        return no == null ? null : no.replaceAll("[\\s-]", "").toUpperCase();
    }

    /**
     * 컨테이너 외부 높이(m). ISO 규격코드 두 번째 글자 기준
     * 0 = 8ft(2.438), 2 = 8ft6in(2.591), 5 = 9ft6in 하이큐브(2.896)
     */
    public static BigDecimal heightM(String isoSizeTypeCode, Boolean highCube) {
        if (Boolean.TRUE.equals(highCube)) return new BigDecimal("2.896");
        if (isoSizeTypeCode != null && isoSizeTypeCode.length() >= 2) {
            switch (isoSizeTypeCode.charAt(1)) {
                case '0': return new BigDecimal("2.438");
                case '5': return new BigDecimal("2.896");
                case '6': return new BigDecimal("2.900");
                default: return new BigDecimal("2.591");
            }
        }
        return new BigDecimal("2.591");
    }
}
