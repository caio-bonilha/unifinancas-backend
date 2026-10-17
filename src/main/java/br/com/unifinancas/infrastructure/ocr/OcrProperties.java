package br.com.unifinancas.infrastructure.ocr;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da cascata de extração de documentos (T3.1.3, ADR-016).
 * Prefixo {@code unifinancas.ocr}.
 */
@ConfigurationProperties(prefix = "unifinancas.ocr")
public class OcrProperties {

    /**
     * Confiança média mínima do OCR (0..1) para a etapa Tesseract ser aceita;
     * abaixo disso a cascata cai para a etapa de IA. `[Proposto]` 0.60 —
     * ajustar por medição real (SAD Seção 17.7).
     */
    private double confiancaMinima = 0.60;

    private final Tesseract tesseract = new Tesseract();
    private final Ia ia = new Ia();
    private final Gemini gemini = new Gemini();
    private final Openai openai = new Openai();

    public double getConfiancaMinima() {
        return confiancaMinima;
    }

    public void setConfiancaMinima(double confiancaMinima) {
        this.confiancaMinima = confiancaMinima;
    }

    public Tesseract getTesseract() {
        return tesseract;
    }

    public Ia getIa() {
        return ia;
    }

    public Gemini getGemini() {
        return gemini;
    }

    public Openai getOpenai() {
        return openai;
    }

    /** Qual provedor de IA de visão a etapa 4 usa (ADR-016). */
    public enum ProvedorIa {
        GEMINI,
        OPENAI
    }

    public static class Ia {
        /** Provedor efetivamente chamado. Padrão: Gemini (decisão consultada). */
        private ProvedorIa provedor = ProvedorIa.GEMINI;

        public ProvedorIa getProvedor() {
            return provedor;
        }

        public void setProvedor(ProvedorIa provedor) {
            this.provedor = provedor;
        }
    }

    public static class Gemini {
        /** Chave da API. Em branco = etapa de IA desabilitada quando o provedor é GEMINI ("bean nulo"). */
        private String apiKey = "";
        /** Modelo de visão. */
        private String modelo = "gemini-2.0-flash";
        /** Base da API {@code generateContent}. */
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        /** Timeout curto da chamada HTTP (SAD Seção 7.6). */
        private Duration timeout = Duration.ofSeconds(30);

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelo() {
            return modelo;
        }

        public void setModelo(String modelo) {
            this.modelo = modelo;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Tesseract {
        /** Comando/caminho do binário do Tesseract (chamado como subprocesso). */
        private String comando = "tesseract";
        /** Pacote(s) de idioma; padrão português. */
        private String idioma = "por";
        /** DPI usado ao rasterizar páginas de PDF antes do OCR. */
        private int dpi = 300;
        /** Tempo máximo de espera pelo subprocesso. */
        private Duration timeout = Duration.ofSeconds(60);

        public String getComando() {
            return comando;
        }

        public void setComando(String comando) {
            this.comando = comando;
        }

        public String getIdioma() {
            return idioma;
        }

        public void setIdioma(String idioma) {
            this.idioma = idioma;
        }

        public int getDpi() {
            return dpi;
        }

        public void setDpi(int dpi) {
            this.dpi = dpi;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Openai {
        /** Chave da API. Em branco = etapa OpenAI desabilitada ("bean nulo"). */
        private String apiKey = "";
        /** Modelo de visão. */
        private String modelo = "gpt-4o-mini";
        /** Base da API (permite apontar para um proxy/compatível). */
        private String baseUrl = "https://api.openai.com/v1";
        /** Timeout curto da chamada HTTP (SAD Seção 7.6). */
        private Duration timeout = Duration.ofSeconds(30);

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelo() {
            return modelo;
        }

        public void setModelo(String modelo) {
            this.modelo = modelo;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
