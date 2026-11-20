package com.carddemo.interest.handoff;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class Digests {
    private Digests() {}

    static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
