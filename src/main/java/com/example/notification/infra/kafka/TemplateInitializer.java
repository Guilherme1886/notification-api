package com.example.notification.infra.kafka;

import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.Template;
import com.example.notification.domain.repository.TemplateRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Seeds the Saga notification templates on startup so the Kafka consumers always
 * have a template to render. Idempotent: existing templates are left untouched.
 */
@Component
public class TemplateInitializer {

    static final String ORDER_CREATED = "order_created";
    static final String PAYMENT_APPROVED = "payment_approved";
    static final String PAYMENT_FAILED = "payment_failed";

    private static final Logger log = LoggerFactory.getLogger(TemplateInitializer.class);

    private final TemplateRepository templates;

    public TemplateInitializer(TemplateRepository templates) {
        this.templates = templates;
    }

    @PostConstruct
    public void seedTemplates() {
        ensure(ORDER_CREATED, "Pedido recebido",
                "Olá, seu pedido {{orderId}} foi recebido e está sendo processado.");
        ensure(PAYMENT_APPROVED, "Pagamento aprovado",
                "Pagamento de R$ {{amount}} aprovado! Seu pedido {{orderId}} está confirmado.");
        ensure(PAYMENT_FAILED, "Pagamento recusado",
                "Pagamento recusado. Motivo: {{reason}}. Pedido {{orderId}} cancelado.");
    }

    private void ensure(String id, String subject, String body) {
        if (templates.findById(id).isPresent()) {
            return;
        }
        templates.save(new Template(id, id, NotificationChannel.EMAIL, subject, body, Instant.now()));
        log.info("Seeded notification template '{}'", id);
    }
}
