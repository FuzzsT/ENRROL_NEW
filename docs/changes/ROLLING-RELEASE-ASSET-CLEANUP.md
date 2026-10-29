# Rolling release asset cleanup

Rolling prereleases (`dpc-aio-emergency-enrollment` and `dpc-aio-continuous`) must not accumulate stale versioned QR release bundles. Before uploading the current verified `dist/` payload, the publish job removes older `DPC-AIO-*-QR-RELEASE-BUNDLE.zip` assets and their `.sha256` sidecars from rolling prereleases only. Stable tag releases remain untouched.
