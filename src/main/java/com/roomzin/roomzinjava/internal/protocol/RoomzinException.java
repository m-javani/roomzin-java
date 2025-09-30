package com.roomzin.roomzinjava.internal.protocol;

public class RoomzinException extends Exception {

    public enum Kind {
        CLIENT, // SDK misuse / auth / config
        REQUEST, // validation, not-found, overflow …
        INTERNAL, // bug, parse failure, protocol mismatch
        RETRY // 429, 503, 308, role change …
    }

    private final Kind kind;
    private final String code;

    /* ---------------------------------------------------------- */
    /* Static factories – mirror Go RzError */
    /* ---------------------------------------------------------- */

    public static RoomzinException of(Object anything) {
        return of(anything, null);
    }

    public static RoomzinException of(Object anything, Kind hint) {
        if (anything == null)
            return null;

        if (anything instanceof RoomzinException) {
            return (RoomzinException) anything;
        }

        if (anything instanceof Throwable) {
            Throwable t = (Throwable) anything;
            String s = t.getMessage();
            if (s == null)
                s = t.getClass().getSimpleName();
            return build(s, hint, t);
        }

        return build(String.valueOf(anything), hint, null);
    }

    /* ---------------------------------------------------------- */
    /* Full constructor (private) */
    /* ---------------------------------------------------------- */

    private RoomzinException(String message, Kind kind, Throwable cause) {
        super(message, cause);

        String c;
        Kind k;

        if (message != null && message.contains(":")) {
            int idx = message.indexOf(':');
            c = message.substring(0, idx);
            k = classifyCode(c);
        } else {
            k = (kind == null ? Kind.INTERNAL : kind);
            c = defaultCode(k);
        }
        this.code = c;
        this.kind = k;
    }

    /* ---------------------------------------------------------- */
    /* Public read-only */
    /* ---------------------------------------------------------- */

    public Kind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }

    public boolean isClient() {
        return kind == Kind.CLIENT;
    }

    public boolean isRequest() {
        return kind == Kind.REQUEST;
    }

    public boolean isInternal() {
        return kind == Kind.INTERNAL;
    }

    public boolean isRetry() {
        return kind == Kind.RETRY;
    }

    /* ---------------------------------------------------------- */
    /* Private helpers – Java 8 style */
    /* ---------------------------------------------------------- */

    private static RoomzinException build(String raw, Kind hint, Throwable cause) {
        String code;
        String msg;
        int idx = raw.indexOf(':');
        if (idx != -1) {
            code = raw.substring(0, idx);
            msg = raw.substring(idx + 1);
        } else {
            code = raw;
            msg = raw;
        }
        Kind k = (hint != null) ? hint : classifyCode(code);
        String full = code + ":" + msg;
        return new RoomzinException(full, k, cause);
    }

    private static Kind classifyCode(String code) {
        String upper = code.toUpperCase(java.util.Locale.ROOT);
        switch (upper) {
            case "AUTH_ERROR":
            case "CLIENT_ERROR":
                return Kind.CLIENT;
            case "VALIDATION_ERROR":
            case "NOT_FOUND":
            case "OVERFLOW":
            case "UNDERFLOW":
            case "FORBIDDEN":
            case "REQUEST_ERROR":
                return Kind.REQUEST;
            case "429":
            case "503":
            case "308":
            case "405":
            case "RETRY_ERROR":
                return Kind.RETRY;
            default:
                return Kind.INTERNAL;
        }
    }

    private static String defaultCode(Kind k) {
        switch (k) {
            case CLIENT:
                return "CLIENT_ERROR";
            case REQUEST:
                return "REQUEST_ERROR";
            case RETRY:
                return "RETRY_ERROR";
            default:
                return "INTERNAL_ERROR";
        }
    }
}