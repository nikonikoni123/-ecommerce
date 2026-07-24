package com.ecommerce.mail;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.ecommerce.config.AppProperties;

import jakarta.mail.internet.MimeMessage;

/**
 * Envio de correos transaccionales con plantillas Thymeleaf.
 *
 * <p>Los envios son asincronos para que un SMTP lento no bloquee la respuesta HTTP del registro,
 * y un fallo de correo se registra pero nunca tumba la operacion de negocio que lo origino.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties properties;

    public MailService(JavaMailSender mailSender, TemplateEngine templateEngine,
                       AppProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @Async
    public void sendEmailVerification(String to, String name, String token) {
        String link = properties.frontendUrl() + "/auth/verificar?token=" + token;
        send(to, "Confirma tu correo electronico", "email-verification",
                Map.of("name", name, "link", link));
    }

    @Async
    public void sendWelcome(String to, String name) {
        send(to, "Tu cuenta ya esta activa", "welcome",
                Map.of("name", name, "link", properties.frontendUrl() + "/productos"));
    }

    @Async
    public void sendTwoFactorCode(String to, String name, String code) {
        send(to, "Tu código de acceso", "2fa-code", 
            Map.of("name", name, "code", code));
    }

    @Async
    public void sendPasswordReset(String to, String name, String token) {
        String link = properties.frontendUrl() + "/auth/restablecer?token=" + token;
        send(to, "Restablece tu contrasena", "password-reset",
                Map.of("name", name, "link", link));
    }

    /** Aviso generico usado por los cambios de estado y las alertas de seguridad. */
    @Async
    public void sendNotice(String to, String name, String subject, String heading, String message) {
        send(to, subject, "notice",
                Map.of("name", name, "heading", heading, "message", message,
                        "link", properties.frontendUrl()));
    }

    private void send(String to, String subject, String template, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            context.setVariable("frontendUrl", properties.frontendUrl());
            String html = templateEngine.process("mail/" + template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mailFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Correo '{}' enviado a {}", template, to);
        } catch (Exception e) {
            // El registro del usuario no debe fallar porque el SMTP no responda.
            log.error("No se pudo enviar el correo '{}' a {}: {}", template, to, e.getMessage());
        }
    }
}
