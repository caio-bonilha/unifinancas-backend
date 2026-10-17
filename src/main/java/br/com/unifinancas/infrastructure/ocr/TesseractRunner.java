package br.com.unifinancas.infrastructure.ocr;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executa o Tesseract como <b>subprocesso</b> (ADR-016: nunca via JNI
 * in-process — um crash do binário nativo não derruba a JVM do worker).
 * Invoca {@code tesseract - - -l <idioma> --psm 6 tsv}: lê a imagem PNG do
 * stdin, escreve TSV no stdout. O TSV traz a confiança por palavra, usada
 * para decidir se a etapa OCR é boa o bastante (limiar configurável).
 *
 * <p>{@link #parseTsv(String)} é estático e sem dependência do binário — é o
 * que os testes exercitam. A chamada real ao {@code tesseract} só roda onde
 * o binário estiver instalado (imagem Docker do backend, ADR-016).
 */
@Component
class TesseractRunner {

    private static final Logger log = LoggerFactory.getLogger(TesseractRunner.class);

    private final OcrProperties.Tesseract config;

    TesseractRunner(OcrProperties propriedades) {
        this.config = propriedades.getTesseract();
    }

    OcrResultado run(byte[] imagemPng) {
        ProcessBuilder pb = new ProcessBuilder(
                config.getComando(), "-", "-", "-l", config.getIdioma(), "--psm", "6", "tsv");
        pb.redirectErrorStream(true); // avisos do tesseract (sem tab) são ignorados no parse do TSV
        Process processo = null;
        try {
            processo = pb.start();

            // Escreve o stdin em outra thread para não travar se o stdout encher antes.
            Process alvo = processo;
            Thread escritor = new Thread(() -> {
                try (OutputStream stdin = alvo.getOutputStream()) {
                    stdin.write(imagemPng);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, "tesseract-stdin");
            escritor.setDaemon(true);
            escritor.start();

            byte[] saida = processo.getInputStream().readAllBytes();
            boolean terminou = processo.waitFor(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            escritor.join(1_000);
            if (!terminou) {
                processo.destroyForcibly();
                throw new OcrIndisponivelException("Tesseract excedeu o timeout de " + config.getTimeout());
            }
            String texto = new String(saida, StandardCharsets.UTF_8);
            if (processo.exitValue() != 0) {
                throw new OcrIndisponivelException("Tesseract saiu com código " + processo.exitValue() + ": "
                        + texto.strip());
            }
            return parseTsv(texto);
        } catch (IOException e) {
            // binário ausente / não executável — cascata trata como "etapa indisponível".
            throw new OcrIndisponivelException("Não foi possível executar o Tesseract (" + config.getComando() + ").", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrIndisponivelException("Interrompido enquanto aguardava o Tesseract.", e);
        } finally {
            if (processo != null && processo.isAlive()) {
                processo.destroyForcibly();
            }
        }
    }

    /**
     * TSV do Tesseract: cabeçalho com colunas fixas; linhas de palavra têm
     * {@code conf} (0..100, ou -1 para linhas estruturais) na coluna 11 e o
     * {@code text} na 12. Confiança média = média das palavras com
     * {@code conf >= 0} e texto não vazio, normalizada para 0..1.
     */
    static OcrResultado parseTsv(String tsv) {
        StringBuilder texto = new StringBuilder();
        double somaConf = 0;
        int palavras = 0;
        int ultimaLinha = -1;

        boolean primeira = true;
        for (String linha : tsv.split("\r?\n")) {
            if (primeira) {
                primeira = false;
                continue; // cabeçalho
            }
            if (linha.isBlank()) {
                continue;
            }
            String[] col = linha.split("\t", -1);
            if (col.length < 12) {
                continue; // avisos do tesseract e linhas malformadas
            }
            double conf;
            try {
                conf = Double.parseDouble(col[10]);
            } catch (NumberFormatException e) {
                continue;
            }
            String palavra = col[11].strip();
            if (conf < 0 || palavra.isEmpty()) {
                continue;
            }
            int numLinha = parseIntSeguro(col[4]);
            if (ultimaLinha != -1 && numLinha != ultimaLinha) {
                texto.append('\n');
            } else if (texto.length() > 0) {
                texto.append(' ');
            }
            texto.append(palavra);
            ultimaLinha = numLinha;
            somaConf += conf;
            palavras++;
        }
        double media = palavras == 0 ? 0.0 : (somaConf / palavras) / 100.0;
        return new OcrResultado(texto.toString(), media);
    }

    private static int parseIntSeguro(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
