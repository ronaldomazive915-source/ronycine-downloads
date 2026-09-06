package com.example.util

sealed class UsernameValidationResult {
    object Valid : UsernameValidationResult()
    data class Invalid(val reason: String) : UsernameValidationResult()
}

object UsernameUtils {

    /**
     * Normaliza o nome de usuário removendo o caractere '@' no início,
     * espaços e convertendo para minúsculas.
     * Exemplo: "@Ronaldo" -> "ronaldo", "  @RONALDO_12  " -> "ronaldo_12"
     */
    fun normalize(rawInput: String): String {
        val trimmed = rawInput.trim()
        val withoutAt = if (trimmed.startsWith("@")) trimmed.drop(1) else trimmed
        return withoutAt.trim().lowercase()
    }

    /**
     * Formata para exibição com o prefixo '@'.
     * Exemplo: "ronaldo" -> "@ronaldo", "@ronaldo" -> "@ronaldo"
     */
    fun formatDisplay(rawInput: String): String {
        val normalized = normalize(rawInput)
        return if (normalized.isEmpty()) "" else "@$normalized"
    }

    /**
     * Valida as regras de negócio para nome de usuário:
     * - Sem o '@', deve ter entre 3 e 20 caracteres.
     * - Apenas caracteres a-z, 0-9, sublinhado (_) e ponto (.).
     * - Não pode conter espaços ou símbolos especiais.
     * - Não pode começar ou terminar com ponto ou ter pontos consecutivos.
     */
    fun validate(rawInput: String): UsernameValidationResult {
        val clean = normalize(rawInput)

        if (clean.isBlank()) {
            return UsernameValidationResult.Invalid("Informe um nome de usuário.")
        }

        if (clean.length < 3) {
            return UsernameValidationResult.Invalid("Mínimo de 3 caracteres.")
        }

        if (clean.length > 20) {
            return UsernameValidationResult.Invalid("Máximo de 20 caracteres.")
        }

        // Permitido: letras minúsculas, números, underline e ponto
        val regex = Regex("^[a-z0-9_.]+$")
        if (!regex.matches(clean)) {
            return UsernameValidationResult.Invalid("Use apenas letras, números, '_' e '.'.")
        }

        if (clean.startsWith(".") || clean.endsWith(".")) {
            return UsernameValidationResult.Invalid("Não pode começar ou terminar com ponto.")
        }

        if (clean.contains("..")) {
            return UsernameValidationResult.Invalid("Não use pontos consecutivos.")
        }

        return UsernameValidationResult.Valid
    }

    /**
     * Identifica se uma string é um e-mail válido ou se deve ser tratada como @username.
     */
    fun isEmailAddress(input: String): Boolean {
        val trimmed = input.trim()
        if (!trimmed.contains("@")) return false
        // Se começar com @ (ex: @ronaldo), é username e NÃO e-mail
        if (trimmed.startsWith("@")) return false
        return android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
    }
}
