# Enrollment Server v2 operator notes

## Stable signing key

Create an Ed25519 private key outside the repository:

```bash
openssl genpkey -algorithm ED25519 -out enrollment-signing-private.pem
openssl pkey -in enrollment-signing-private.pem -pubout -outform DER | base64 -w0 > enrollment-signing-public-x509-base64.txt
```

Configure the server with the private key path:

```bash
export DPC_AIO_ENROLLMENT_SIGNING_PRIVATE_KEY_FILE=/secure/enrollment-signing-private.pem
export DPC_AIO_TOKEN_STORE_PATH=/var/lib/dpc-aio/enrollment-token-store.json
node services/provisioning/src/server.mjs
```

Configure the DPC build with the matching public key:

```bash
export DPC_AIO_ENROLLMENT_SIGNING_PUBLIC_KEY="$(cat enrollment-signing-public-x509-base64.txt)"
export DPC_AIO_ENROLLMENT_ENDPOINT=https://enroll.example.com
```

Do not copy the private key into the source repository, APK, diagnostics, QR, or release archive.

## Token lifecycle

`ISSUED → RESERVED → COMMITTED` is the successful path. An expired reservation returns a still-valid token to `ISSUED`. Expired/revoked/committed tokens cannot be reused. The persistent store contains token SHA-256 digests, not plaintext token values.

## Development-only signing

`DPC_AIO_ALLOW_EPHEMERAL_SIGNING_KEY=1` is intentionally explicit and unsuitable for stable deployments because the public trust anchor changes after server restart.
