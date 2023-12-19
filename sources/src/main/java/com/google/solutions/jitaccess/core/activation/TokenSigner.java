package com.google.solutions.jitaccess.core.activation;

import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.clients.IamCredentialsClient;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Signs JWTs using a service account's Google-managed service account
 * ket.
 */
@ApplicationScoped
public class TokenSigner {
  private final IamCredentialsClient iamCredentialsClient;
  private final Options options;
  private final TokenVerifier tokenVerifier;

  public TokenSigner(
    IamCredentialsClient iamCredentialsClient,
    Options options
  ) {
    this.options = options;
    this.iamCredentialsClient = iamCredentialsClient;

    //
    // Create verifier to check signature and obligatory claims.
    //
    this.tokenVerifier = TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsClient.getJwksUrl(options.serviceAccount))
      .setIssuer(options.serviceAccount.email)
      .setAudience(options.serviceAccount.email)
      .build();
  }

  /**
   * Create a signed JWT for a given payload.
   */
  public <T> TokenWithExpiry sign(
    JsonWebTokenConverter<T> converter,
    T payload
  ) throws AccessException, IOException {

    Preconditions.checkNotNull(converter, "converter");
    Preconditions.checkNotNull(payload, "payload");

    //
    // Add obligatory claims.
    //
    var issueTime = Instant.now();
    var expiryTime = issueTime.plus(this.options.tokenValidity);
    var jwtPayload =  converter.convert(payload)
      .setAudience(this.options.serviceAccount.email)
      .setIssuer(this.options.serviceAccount.email)
      .setIssuedAtTimeSeconds(issueTime.getEpochSecond())
      .setExpirationTimeSeconds(expiryTime.getEpochSecond());

    return new TokenWithExpiry(
      this.iamCredentialsClient.signJwt(this.options.serviceAccount, jwtPayload),
      issueTime,
      expiryTime);
  }

  /**
   * Decode and verify a JWT.
   */
  public <T> T verify(
    JsonWebTokenConverter<T> converter,
    String token
  ) throws TokenVerifier.VerificationException {

    Preconditions.checkNotNull(converter, "converter");
    Preconditions.checkNotNull(token, "token");

    //
    // Verify the token against the service account's JWKs. If that succeeds, we know
    // that the token has been issued by us.
    //
    var decodedToken = this.tokenVerifier.verify(token);
    if (!decodedToken.getHeader().getAlgorithm().equals("RS256")) {
      //
      // Service account keys are RS256, anything else is fishy.
      //
      throw new TokenVerifier.VerificationException("The token uses the wrong algorithm");
    }

    return converter.convert(decodedToken.getPayload());
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public record TokenWithExpiry(
    String token,
    Instant issueTime,
    Instant expiryTime) {
    public TokenWithExpiry {
      Preconditions.checkNotNull(token, "token");
      Preconditions.checkArgument(expiryTime.isAfter(issueTime));
      Preconditions.checkArgument(expiryTime.isAfter(Instant.now()));
    }
  }

  public record Options(UserId serviceAccount, Duration tokenValidity) {
    public Options {
      Preconditions.checkNotNull(serviceAccount);
      Preconditions.checkArgument(!tokenValidity.isNegative());
    }
  }
}
