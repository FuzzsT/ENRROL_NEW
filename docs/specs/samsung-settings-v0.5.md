# Samsung Settings Editor v0.5

- Supports Settings.System, Settings.Secure and Settings.Global read-back.
- Write routes are explicit and audited: public WRITE_SETTINGS, typed Shizuku settings command, or actual systemPrivileged WRITE_SECURE_SETTINGS.
- A multi-read stability window detects values that are accepted and then restored by another system/OEM policy.
- ContentObserver reports subsequent reversion after the write.
- Knox Deep Settings Customization is represented as a dedicated route and must only be marked available when a real Knox/KSP adapter and license are present.
- No hidden-API enforcement bypass is used by enterprise builds.
