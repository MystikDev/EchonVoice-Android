package com.echon.voice.core.network

import okhttp3.CertificatePinner

/**
 * Certificate pinning for echon-voice.com.
 *
 * Ported from the iOS `PinningDelegate`: pins the long-lived ISRG roots
 * (X1 + X2) rather than the rotating Let's Encrypt intermediates. OkHttp pins
 * the SPKI SHA-256, which is what [CertificatePinner] expects — the two values
 * below were derived from the same `isrgrootx1.der` / `isrgrootx2.der` bundled
 * in the iOS app:
 *
 *   openssl x509 -in isrgrootx1.der -inform der -pubkey -noout \
 *     | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary | base64
 *
 * The resulting X1 pin matches the value the legacy WebView wrapper already
 * shipped in its network_security_config.xml, cross-validating the derivation.
 *
 * This pinner is shared by the REST client, the WebSocket, the refresh client,
 * and Coil image loading — every connection those make to the host fails closed
 * if neither root is in the evaluated chain.
 *
 * Note: LiveKit (voice) builds its own internal OkHttp stack and does NOT use
 * this pinner. Its connection to `wss://echon-voice.com/lk` is instead pinned by
 * the platform network_security_config.xml (same ISRG roots, includeSubdomains),
 * which Android enforces on every socket regardless of client. If LiveKit ever
 * moves off echon-voice.com, that platform pin would no longer cover it — pass
 * this pinned client into LiveKit.create() at that point.
 */
object TlsPinning {
    const val HOST = "echon-voice.com"

    /**
     * Whether [host] is the Echon API origin (the host itself or a subdomain).
     * The single gate for attaching credentials / accepting auth cookies — used by
     * the auth interceptor, the 401 authenticator, and the refresh-cookie capture
     * so none of them ever trust a third-party host (e.g. an image URL).
     */
    fun isApiHost(host: String): Boolean = host == HOST || host.endsWith(".$HOST")

    /** ISRG Root X1 SPKI SHA-256. */
    const val ISRG_ROOT_X1 = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="

    /** ISRG Root X2 SPKI SHA-256. */
    const val ISRG_ROOT_X2 = "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI="

    fun certificatePinner(): CertificatePinner =
        CertificatePinner.Builder()
            // Two entries: the apex host, plus `*.` which in OkHttp matches exactly
            // one subdomain level (api., ws., etc.). All Echon endpoints live on the
            // apex or a single-level subdomain, so this is sufficient; add `**.` only
            // if a multi-level host is ever introduced.
            .add(HOST, ISRG_ROOT_X1, ISRG_ROOT_X2)
            .add("*.$HOST", ISRG_ROOT_X1, ISRG_ROOT_X2)
            .build()
}
