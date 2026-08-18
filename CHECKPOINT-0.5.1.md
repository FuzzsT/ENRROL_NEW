# DPC-AIO checkpoint 0.5.1

Adds an offline **test-only** Knox/KLM simulation path for the `lab` product flavor.

## LAB-KLM
- Token prefix: `DPC-AIO-LAB1`
- Signature: ECDSA P-256 / SHA-256
- Audience: `io.dpcaio.app`
- Allowed build tracks: `lab`, `tst`, `eng`
- Production/prd is always rejected by `KnoxLabLicenseVerifier`.
- The token is not a Samsung KLM/KPE key and is not sent to Knox APIs.

## Files
- `knox-license-core/.../KnoxLabLicense.kt` — verifier and claims model.
- `app-dpc/src/lab/.../KnoxLabLicenseProvider.kt` — lab-only provider.
- `app-dpc/src/lab/assets/knox_lab/` — test token + verification public key.
- `lab-license/` — private LAB signer key, public key, token, claims and offline generator.

## Safety boundary
No test private key is packaged into `app-dpc` assets. `enterprise` and `systemPrivileged` do not depend on `knox-license-core`; only `labImplementation` does.
