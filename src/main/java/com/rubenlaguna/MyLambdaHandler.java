package com.rubenlaguna;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;


import static java.lang.System.*;


public class MyLambdaHandler {

    private final String username;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String fingerprint;

    public MyLambdaHandler() throws Exception {
        final AWSSecretsManager sm = AWSSecretsManagerClientBuilder.defaultClient();
        GetSecretValueRequest req = new GetSecretValueRequest().withSecretId(System.getenv("SECRET_ID"));
        String secret = sm.getSecretValue(req).getSecretString();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(secret);
        this.username = jsonNode.get("username").asText();
        final String private_key_pem = jsonNode.get("private_key").asText();

        final Pattern parse = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*");
        final String encoded = parse.matcher(private_key_pem).replaceFirst("$1");
        final byte[] decoded = Base64.getMimeDecoder().decode(encoded);
        final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        final KeyFactory kf = KeyFactory.getInstance("RSA");
        final RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) kf.generatePrivate(keySpec);

        final RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
        final PublicKey publicKey = kf.generatePublic(publicKeySpec);

        // get the publick key as DER SubjectPublicKeyInfo
        byte[] der = publicKey.getEncoded();
        // Calculate SHA-256 of the DER
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyDigest = digest.digest(der);

        // encode base64 the SHA-256
        String b64 = Base64.getEncoder().encodeToString(keyDigest);
        // Create string "SHA256:<base64 hash>"
        this.fingerprint = String.format("SHA256:%s", b64);

        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public void handleRequest(S3Event input, Context context)  throws Exception {
        S3EventNotification.S3EventNotificationRecord record = input.getRecords().get(0);
        String key = record.getS3().getObject().getKey();
        context.getLogger().log(String.format("S3 key: %s", key));


        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime expiration = now.plusMinutes(5);

        String jws = Jwts.builder()
                .setIssuer(String.format("%s.%s",this.username, this.fingerprint))
                .setSubject(this.username)
                .setIssuedAt(Date.from(now.toInstant()))
                .setExpiration(Date.from(expiration.toInstant()))
                .signWith(this.privateKey)
                .compact();

        context.getLogger().log(String.format("%s %s", key, jws));
    }
}
