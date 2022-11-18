package com.google.solutions.jitaccess.core.services;

import com.google.common.base.Preconditions;
import com.google.common.html.HtmlEscapers;
import com.google.solutions.jitaccess.core.adapters.MailAdapter;
import com.google.solutions.jitaccess.core.adapters.UserId;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for delivering notifications.
 */
public class NotificationService {
  private final Options options;
  private final MailAdapter mailAdapter;
  private final String emailTemplate;

  public NotificationService(
    MailAdapter mailAdapter,
    Options options) {
    Preconditions.checkNotNull(mailAdapter, "mailAdapter");
    Preconditions.checkNotNull(options, "options");

    this.mailAdapter = mailAdapter;
    this.options = options;

    //
    // Read email template file from JAR.
    //
    try (var stream = NotificationService.class
      .getClassLoader()
      .getResourceAsStream("ApprovalRequest.email.html")) {
      this.emailTemplate = new String(stream.readAllBytes());
    }
    catch (IOException e) {
      throw new RuntimeException("The JAR file is missing the email template", e);
    }
  }

  public void sendNotification(Notification notification) throws NotificationException {
    Preconditions.checkNotNull(notification, "notification");

    if (this.options.enableEmail) {
      try {
        this.mailAdapter.sendMail(
          notification.recipient.getEmail(),
          notification.recipient.getEmail(),
          notification.subject,
          notification.format());
      }
      catch (MailAdapter.MailException e) {
        throw new NotificationException("The notification could not be sent", e);
      }
    }
    else {
      System.out.println(notification.toString());
    }
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  /** Generic notification that can be formatted as a (HTML) email */
  public static abstract class Notification {
    private final String template;
    private final UserId recipient;
    private final String subject;

    protected Map<String, String> properties = new HashMap<>();

    protected Notification(String template, UserId recipient, String subject) {
      Preconditions.checkNotNull(template, "template");
      Preconditions.checkNotNull(recipient, "recipient");
      Preconditions.checkNotNull(subject, "subject");

      this.template = template;
      this.recipient = recipient;
      this.subject = subject;

      this.properties.put("{{SUBJECT}}", subject);
    }

    protected String format() {
      //
      // Read email template file from JAR and replace {{PROPERTY}} placeholders.
      //
      try (var stream = NotificationService.class
        .getClassLoader()
        .getResourceAsStream("ApprovalRequest.email.html")) {

        var template = new String(stream.readAllBytes());
        var escaper = HtmlEscapers.htmlEscaper();

        for (var property : this.properties.entrySet()) {
          template = template.replace(
            property.getKey(),
            escaper.escape(property.getValue()));
        }

        return template;
      }
      catch (IOException e) {
        throw new RuntimeException(
          String.format("The JAR file does not contain an template named %s", this.template), e);
      }
    }

    @Override
    public String toString() {
      return String.format(
        "Notification to %s: %s\n\n%s",
        this.recipient,
        this.subject,
        this.properties
          .entrySet()
          .stream()
          .map(e -> String.format(" %s: %s", e.getKey(), e.getValue()))
          .collect(Collectors.joining("\n", "", "")));
    }
  }

  public static class ApprovalRequest extends Notification {
    private static final String TEMPLATE = "ApprovalRequest.email.html";

    public ApprovalRequest(
      UserId requestor,
      UserId recipient,
      RoleBinding roleBinding,
      String justification,
      URI actionLink) {
      super(
        TEMPLATE,
        recipient,
        String.format("%s requests your approval to access a Google Cloud resource", requestor.getEmail()));

      super.properties.put("{{REQUESTOR}}", requestor.getEmail());
      super.properties.put("{{RESOURCE}}", roleBinding.getResourceName());
      super.properties.put("{{ROLE}}", roleBinding.getRole());
      super.properties.put("{{JUSTIFICATION}}", justification);
      super.properties.put("{{ACTION_LINK}}", actionLink.toString());
    }
  }

  public static class Options {
    private final boolean enableEmail;

    public Options(boolean enableEmail) {
      this.enableEmail = enableEmail;
    }
  }

  public class NotificationException extends Exception {
    public NotificationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
