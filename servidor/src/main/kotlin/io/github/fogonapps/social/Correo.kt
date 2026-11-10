package io.github.fogonapps.social

import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

/** Quien manda los correos (el código de acceso). En las pruebas se cambia por uno que solo los guarda. */
fun interface Cartero {
    fun enviar(para: String, asunto: String, texto: String)
}

/**
 * Manda por SMTP con la cuenta de Fogón (Gmail, puerto 587 con STARTTLS y una contraseña de aplicación). Se configura
 * con `SMTP_USUARIO` y `SMTP_CLAVE` (en /etc/fogon-social/correo.env, que solo lee root); sin ellas, no manda nada.
 * Opcionales: `SMTP_SERVIDOR` (por defecto smtp.gmail.com), `SMTP_PUERTO` (587, con STARTTLS) y `SMTP_REMITENTE`.
 */
class CarteroSmtp(
    private val usuario: String, private val clave: String, private val servidor: String = "smtp.gmail.com", private val puerto: Int = 587,
    /** La dirección que aparece como remitente; por defecto, la misma cuenta. */
    private val remitente: String = usuario,
) : Cartero {
    private val sesion = Session.getInstance(
        Properties().apply {
            put("mail.smtp.host", servidor); put("mail.smtp.port", puerto.toString())
            put("mail.smtp.auth", "true"); put("mail.smtp.starttls.enable", "true"); put("mail.smtp.starttls.required", "true")
            put("mail.smtp.connectiontimeout", "10000"); put("mail.smtp.timeout", "10000")
        },
        object : jakarta.mail.Authenticator() { override fun getPasswordAuthentication() = PasswordAuthentication(usuario, clave) },
    )

    override fun enviar(para: String, asunto: String, texto: String) {
        val m = MimeMessage(sesion).apply {
            setFrom(InternetAddress(remitente, "Fogón Social", "UTF-8"))
            setRecipient(Message.RecipientType.TO, InternetAddress(para))
            setSubject(asunto, "UTF-8")
            setText(texto, "UTF-8")
        }
        Transport.send(m)
    }
}
