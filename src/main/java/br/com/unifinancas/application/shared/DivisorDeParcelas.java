package br.com.unifinancas.application.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Divide um valor monetario em N parcelas iguais - a ultima absorve o resto
 * da divisao, para a soma bater exatamente com o total. Extraido de
 * {@code FaturaService} (RN-11, ADR-017, T2.1.4) quando {@code CompraVendaService}
 * (T2.2.2) precisou do mesmo algoritmo para distribuir uma Compra parcelada.
 */
public final class DivisorDeParcelas {

    private static final int ESCALA_MONETARIA = 2;

    private DivisorDeParcelas() {
    }

    public static List<BigDecimal> dividirEmParcelasIguais(BigDecimal total, int numeroParcelas) {
        BigDecimal valorParcela = total.divide(BigDecimal.valueOf(numeroParcelas), ESCALA_MONETARIA, RoundingMode.HALF_UP);
        List<BigDecimal> parcelas = new ArrayList<>();
        BigDecimal somaParcelasAnteriores = BigDecimal.ZERO;
        for (int indice = 0; indice < numeroParcelas - 1; indice++) {
            parcelas.add(valorParcela);
            somaParcelasAnteriores = somaParcelasAnteriores.add(valorParcela);
        }
        parcelas.add(total.subtract(somaParcelasAnteriores));
        return parcelas;
    }
}
