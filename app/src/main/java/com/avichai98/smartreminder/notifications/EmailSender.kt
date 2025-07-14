package com.avichai98.smartreminder.notifications

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class EmailSender(
    private val smtpHost: String = "smtp.gmail.com",
    private val smtpPort: String = "587",
    private val username: String = "smart.reminder98@gmail.com",
    private val password: String = "sugvhcuypivqbwkn"
) {
    companion object {
        private const val TAG = "EmailSender"
    }

    suspend fun sendEmail(
        subject: String,
        body: String,
        recipientEmail: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", smtpHost)
                put("mail.smtp.port", smtpPort)
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail))
                setSubject(subject)
                setText(body)
            }

            Transport.send(message)
            Log.d(TAG, "Email sent successfully to $recipientEmail")
            true
        } catch (e: MessagingException) {
            Log.e(TAG, "Failed to send email: ${e.message}", e)
            false
        }
    }
}
