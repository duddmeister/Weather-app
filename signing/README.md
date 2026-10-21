# Release signing key

Android only installs an update over an existing app when both are signed with the same key, so every
release of Nimbus must be signed with one stable key. `nimbus-release.jks` is that key. It's committed
so the release workflow works with no setup.

- Store password and key password: `nimbus-weather`
- Key alias: `nimbus`

**Trade-off:** this repository is public, so anyone could sign an APK that Android accepts as an
update to Nimbus. Your phone only pulls updates from this repository's own GitHub Releases, so in
practice that only matters if you install an APK from somewhere else.

## Using a private key instead

Add these repository secrets (**Settings → Secrets and variables → Actions**). The release workflow
uses them automatically when they're set:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 your-key.jks` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

Switching keys means the next release can't install over the copy already on your phone. Uninstall
Nimbus once, then install the new release; your settings and places will need to be set up again.
