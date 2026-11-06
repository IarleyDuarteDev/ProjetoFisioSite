package br.com.fisioclinica.service;

/** Normalização, validação dos dígitos verificadores e máscara de CPF. */
public final class Cpf {
    private Cpf() {}

    public static String normalizar(String valor) { return valor == null ? "" : valor.replaceAll("\\D", ""); }

    public static boolean valido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) return false;
        return cpf.endsWith(digitos(cpf.substring(0, 9)));
    }
    /** Calcula os dois dígitos verificadores a partir dos 9 primeiros dígitos. */
    public static String digitos(String base) {
        int primeiro = digito(base);
        return primeiro + "" + digito(base + primeiro);
    }
    private static int digito(String base) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) soma += (base.charAt(i) - '0') * (base.length() + 1 - i);
        int resto = soma * 10 % 11;
        return resto == 10 ? 0 : resto;
    }
    public static String mascarar(String cpf) { return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**"; }
}
