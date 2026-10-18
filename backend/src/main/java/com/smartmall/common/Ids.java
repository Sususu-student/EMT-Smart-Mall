package com.smartmall.common;

import java.util.UUID;

public final class Ids {

    private Ids() {
    }

    public static String next() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String shortId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
