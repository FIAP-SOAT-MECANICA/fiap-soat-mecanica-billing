package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies the {@code x-signature} header of Mercado Pago notifications: an HMAC-SHA256, keyed with the webhook
 * secret, of {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} where each part is dropped if absent.
 * Fails closed: without a secret nothing is valid.
 */
class AssinaturaMercadoPago {
    private final byte[] segredo;

    AssinaturaMercadoPago(String segredo) {
        this.segredo = segredo == null ? new byte[0] : segredo.getBytes(StandardCharsets.UTF_8);
    }

    boolean valida(String xSignature, String xRequestId, String dataId) {
        if (segredo.length == 0 || xSignature == null || dataId == null || dataId.isBlank()) return false;
        String ts = null, v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] chaveValor = parte.trim().split("=", 2);
            if (chaveValor.length != 2) continue;
            if (chaveValor[0].equals("ts")) ts = chaveValor[1];
            else if (chaveValor[0].equals("v1")) v1 = chaveValor[1];
        }
        if (ts == null || v1 == null) return false;

        // The provider lowercases alphanumeric ids before signing.
        String id = dataId.matches("[A-Za-z0-9]+") ? dataId.toLowerCase() : dataId;
        var manifesto = new StringBuilder("id:").append(id).append(';');
        if (xRequestId != null && !xRequestId.isBlank()) manifesto.append("request-id:").append(xRequestId).append(';');
        manifesto.append("ts:").append(ts).append(';');

        String esperado = hmac(manifesto.toString());
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
