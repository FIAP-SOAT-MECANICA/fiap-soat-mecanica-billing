package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AssinaturaMercadoPagoTest {
    static final String SEGREDO = "segredo-do-webhook";
    final AssinaturaMercadoPago assinatura = new AssinaturaMercadoPago(SEGREDO);

    static String assinar(String segredo, String manifesto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void aceitaAAssinaturaDoManifestoCompleto() throws Exception {
        String v1 = assinar(SEGREDO, "id:123456;request-id:req-1;ts:1704908010;");
        assertTrue(assinatura.valida("ts=1704908010,v1=" + v1, "req-1", "123456"));
        assertTrue(assinatura.valida(" v1=" + v1.toUpperCase() + " , ts=1704908010", "req-1", "123456"));
    }

    @Test void idAlfanumericoEhAssinadoEmMinusculas() throws Exception {
        String v1 = assinar(SEGREDO, "id:abc123;request-id:req-1;ts:1;");
        assertTrue(assinatura.valida("ts=1,v1=" + v1, "req-1", "ABC123"));
    }

    @Test void semRequestIdOManifestoOmiteAParte() throws Exception {
        String v1 = assinar(SEGREDO, "id:99;ts:5;");
        assertTrue(assinatura.valida("ts=5,v1=" + v1, null, "99"));
        assertTrue(assinatura.valida("ts=5,v1=" + v1, " ", "99"));
    }

    @Test void recusaAssinaturaAdulteradaOuDeOutroSegredo() throws Exception {
        String v1 = assinar(SEGREDO, "id:1;request-id:r;ts:1;");
        assertFalse(assinatura.valida("ts=1,v1=" + v1, "r", "2")); // another payment id
        assertFalse(assinatura.valida("ts=2,v1=" + v1, "r", "1")); // another timestamp
        assertFalse(assinatura.valida("ts=1,v1=" + v1, "outro", "1")); // another request
        assertFalse(assinatura.valida("ts=1,v1=" + assinar("errado", "id:1;request-id:r;ts:1;"), "r", "1"));
        assertFalse(assinatura.valida("ts=1,v1=00", "r", "1"));
    }

    @Test void recusaCabecalhoIncompletoOuAusente() {
        assertFalse(assinatura.valida(null, "r", "1"));
        assertFalse(assinatura.valida("", "r", "1"));
        assertFalse(assinatura.valida("ts=1", "r", "1"));
        assertFalse(assinatura.valida("v1=abc", "r", "1"));
        assertFalse(assinatura.valida("lixo,ts=1,v1=abc", "r", "1"));
        assertFalse(assinatura.valida("ts=1,v1=abc", "r", null));
        assertFalse(assinatura.valida("ts=1,v1=abc", "r", " "));
    }

    @Test void semSegredoNadaEhValido() throws Exception {
        String v1 = assinar(SEGREDO, "id:1;request-id:r;ts:1;");
        assertFalse(new AssinaturaMercadoPago("").valida("ts=1,v1=" + v1, "r", "1"));
        assertFalse(new AssinaturaMercadoPago(null).valida("ts=1,v1=" + v1, "r", "1"));
    }
}
