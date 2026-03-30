package com.socialmedia.auth.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Password Reset Request");

            String htmlContent = """
                    <html>
                    <body style="font-family: Arial, sans-serif; color: #333;">
                        <h2>Password Reset Request</h2>
                        <p>You requested to reset your password. Click the button below to set a new password:</p>
                        <p style="margin: 24px 0;">
                            <a href="%s"
                               style="background-color: #4F46E5; color: white; padding: 12px 24px;
                                      text-decoration: none; border-radius: 6px; font-weight: bold;">
                                Reset Password
                            </a>
                        </p>
                        <p>This link will expire in <strong>15 minutes</strong>.</p>
                        <p>If you did not request a password reset, please ignore this email.</p>
                    </body>
                    </html>
                    """.formatted(resetLink);

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
}
