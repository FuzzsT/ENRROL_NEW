# Enterprise Policy Hub 0.7.0

DPC-AIO 0.7.0 adds a runtime management context and capability resolver, then uses them in Module Center and Enterprise Policy Hub.

Policy surfaces use documented Android APIs only. USB data signaling is API 31+, NFC radio restriction API 35+, and Android 16/API 36 adds automatic time/timezone policies, Thread restriction, NFC change lock and App Functions policy. Android 17/API 37 adds the local-network runtime permission surface used per target package.

Lab/experimental modules remain compiled in the monolithic APK. They are hidden by default and shown through explicit UI preferences.
