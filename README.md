# uskoag-gservices

Java libraries for simplified access to Google Workspace services (Drive, Docs, Sheets, Slides) with OAuth authentication.

## Modules

This is a multi-module Maven project with the following artifacts:

| Artifact | Description | Maven Coordinates |
|----------|-------------|-------------------|
| **uskoag-gservices-oauth** | OAuth token management with credential storage | `io.github.uskoag:uskoag-gservices-oauth:1.0` |
| **uskoag-gservices-sheets** | Google Sheets API service | `io.github.uskoag:uskoag-gservices-sheets:1.0` |
| **uskoag-gservices-drive** | Google Drive API service | `io.github.uskoag:uskoag-gservices-drive:1.0` |
| **uskoag-gservices-docs** | Google Docs API service | `io.github.uskoag:uskoag-gservices-docs:1.0` |
| **uskoag-gservices-slides** | Google Slides API service | `io.github.uskoag:uskoag-gservices-slides:1.0` |

## Requirements

- Java 21+
- Maven 3.6+

## Building

Build all modules:
```bash
mvn clean install
```

## Usage

Each service module provides a simple static factory method for creating the respective Google API service.

### Example: Google Sheets

```java
import uskoag.gservices.OAuthToken;
import uskoag.gservices.SheetsService;
import com.google.api.services.sheets.v4.SheetsScopes;

import static uskoag.gservices.OAuthToken.*;

public class Example {
    public static void main(String[] args) throws Exception {
        var oauthToken = oauthToken("App-name", "app-key-readonly-xyz",
                SheetsScopes.SPREADSHEETS_READONLY,
                SheetsScopes.DRIVE
        )
        .credential("someemail@gmail.com"); // optional

        var sheets = SheetsService.sheets(oauthToken);

        // Use the sheets service...
    }
}
```

### Example: Google Drive

```java
import uskoag.gservices.DriveService;
import com.google.api.services.drive.DriveScopes;

var oauthToken = oauthToken("App-name", "app-key",
        DriveScopes.DRIVE_READONLY
).credential("someemail@gmail.com");

var drive = DriveService.drive(oauthToken);
```

### Example: Google Docs

```java
import uskoag.gservices.DocsService;
import com.google.api.services.docs.v1.DocsScopes;

var oauthToken = oauthToken("App-name", "app-key",
        DocsScopes.DOCUMENTS_READONLY
).credential("someemail@gmail.com");

var docs = DocsService.docs(oauthToken);
```

### Example: Google Slides

```java
import uskoag.gservices.SlidesService;
import com.google.api.services.slides.v1.SlidesScopes;

var oauthToken = oauthToken("App-name", "app-key",
        SlidesScopes.PRESENTATIONS_READONLY
).credential("someemail@gmail.com");

var slides = SlidesService.slides(oauthToken);
```

## OAuth Credentials Setup

Credentials are stored in: `<userhome>/uskoag/gdrive_gdocs_auth/<email>/credentials.json`

Each email account folder should contain:
- `credentials.json` - OAuth client credentials from Google Cloud Console
- `tokens_<hashed-app-key>/` - Auto-generated token storage

## Security Features

### Encrypted Token Storage

OAuth tokens are automatically encrypted at rest using AES-256-GCM to protect against infostealer malware. The app-key is used to derive the encryption key, ensuring tokens are useless even if an attacker gains filesystem access.

**Security benefits:**
- Protects refresh tokens from commodity malware scanning for credentials
- Per-app isolation: each app's unique key encrypts its own tokens
- Transparent: no code changes required, encryption happens automatically
- No UX impact: no password prompts, deterministic key derivation

**Best practices:**
```java
// ✅ Good: Use unique app-key per application
oauthToken("MyApp-ReadOnly-v1.0", "unique-key-12345", scopes...)

// ❌ Bad: Reusing same key across multiple apps
oauthToken("App1", "readonly", scopes...)
oauthToken("App2", "readonly", scopes...)  // Same key = security breach
```

**Technical details:**
- Algorithm: AES-256-GCM with random IVs
- Key derivation: SHA-256(app-key) → 256-bit AES key
- Authentication: 128-bit GCM tag prevents tampering
- Storage format: `[12-byte IV][ciphertext + auth tag]`

## Design Philosophy

- **Separate modules** - Each service is a separate Maven artifact to avoid unnecessary dependencies
- **Lightweight** - Only include dependencies you actually need
- **Simple API** - Static factory methods for easy service creation
- **Centralized versioning** - Parent POM manages all Google API versions
- **Security first** - Encrypted token storage protects against credential theft
