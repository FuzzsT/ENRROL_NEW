# DPC-AIO LAB KLM

This directory contains a local **test-only** license used by DPC-AIO `lab/tst/eng` workflows.
It is not a Samsung KLM/KPE key and Samsung Knox APIs will not accept it.

Files:
- `dpc-aio-lab-klm.token` — generated test token for `io.dpcaio.app`.
- `dpc-aio-lab-public.pem` — verification key; safe to embed in lab builds.
- `dpc-aio-lab-private.pem` — LAB signing key; keep out of production builds.
- `generate-lab-klm.py` — creates replacement offline tokens.
- `claims.txt` — decoded claims of the shipped test token.

The production/enterprise build must always reject this token. Only the `lab` source set contains `KnoxLabLicenseProvider`.
