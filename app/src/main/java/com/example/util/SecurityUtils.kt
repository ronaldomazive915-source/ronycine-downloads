package com.example.util

import java.security.MessageDigest

object SecurityUtils {
    /**
     * Calcula o hash SHA-256 de uma string (como um PIN de 4 dígitos).
     */
    fun hashPin(pin: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(pin.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Em caso improvável de erro, retorna um fallback (não ideal para produção real)
            pin
        }
    }

    /**
     * Verifica se um PIN corresponde ao hash fornecido.
     * Trata de forma segura o hash SHA-256 e documentos legados.
     * Retorna false se hash for nulo ou se pin for vazio.
     */
    fun verifyPin(pin: String, expectedHashOrPlain: String?): Boolean {
        if (expectedHashOrPlain.isNullOrBlank()) return false
        val cleanPin = pin.trim()
        val cleanExpected = expectedHashOrPlain.trim()
        if (cleanPin.isEmpty()) return false
        
        // Compatibilidade com cadastros antigos onde o PIN de 4 dígitos foi salvo direto
        if (cleanExpected == cleanPin) return true
        
        // Comparação com hash SHA-256
        val calculatedHash = hashPin(cleanPin)
        return calculatedHash.equals(cleanExpected, ignoreCase = true)
    }
}
