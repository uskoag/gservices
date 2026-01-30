# uskoag-gservices-youtube

YouTube Data API v3 service wrapper with OAuth integration for the uskoag-gservices project.

## Maven Coordinates

```xml
<dependency>
    <groupId>io.github.uskoag</groupId>
    <artifactId>uskoag-gservices-youtube</artifactId>
    <version>1.0</version>
</dependency>
```

## Quick Start

```java
import uskoag.gservices.OAuthToken;
import uskoag.gservices.YouTubeService;

import static uskoag.gservices.OAuthToken.oauthToken;

// Create OAuth token with appropriate scope
var oauthToken = oauthToken("My-YouTube-App", "unique-app-key",
        "https://www.googleapis.com/auth/youtube.readonly"
).credential("user@gmail.com");

// Create YouTube service
var youtube = YouTubeService.youtube(oauthToken);

// Use the service
var searchRequest = youtube.search().list(List.of("snippet"));
searchRequest.setQ("Java programming");
var results = searchRequest.execute();
```

## Available OAuth Scopes

| Scope | Description | Use Case |
|-------|-------------|----------|
| `youtube` | Full access to YouTube account | Upload, edit, delete videos; manage playlists |
| `youtube.readonly` | Read-only access | Search, view statistics, read comments |
| `youtube.force-ssl` | Full access with SSL enforcement | Live chat operations, content management |
| `youtube.upload` | Upload videos only | Limited to video uploads |
| `youtubepartner` | YouTube Partner access | Partner-specific features |
| `youtube.channel-memberships.creator` | Channel memberships | Manage channel memberships |

Full scope URLs: `https://www.googleapis.com/auth/[scope]`

## API Capabilities

### Video Management
- Upload, update, delete videos
- Get video details, statistics, content details
- Rate videos (like/dislike)
- Report videos

### Live Streaming
- **Live chat messages**: Read, post, delete messages
- **Live chat streaming**: Low-latency real-time message retrieval
- **Broadcasts**: Manage live broadcasts
- **Moderation**: Moderate live chat (requires owner/moderator role)

### Channel Operations
- Get channel information and statistics
- Manage subscriptions
- Channel sections and featured content
- Channel memberships

### Playlists
- Create, update, delete playlists
- Add/remove videos from playlists
- Reorder playlist items

### Comments
- Read comments and comment threads
- Post comments on videos
- Moderate comments

### Search & Discovery
- Search videos, channels, playlists
- Filter by various criteria
- Get search suggestions

### Content Management
- Upload and manage captions/subtitles
- Manage thumbnails and watermarks
- Video abuse reporting

## Examples

Comprehensive usage examples are available in `src/test/java/examples/YouTubeExamples.java`, including:

1. **Live Chat Operations**
   - Reading live chat messages
   - Posting messages to live chat
   - Deleting messages (moderation)

2. **Video Operations**
   - Searching for videos
   - Getting video details and statistics
   - Uploading videos
   - Updating video metadata

3. **Channel Operations**
   - Getting channel information
   - Subscribing to channels

4. **Playlist Operations**
   - Creating playlists
   - Adding videos to playlists

5. **Comment Operations**
   - Reading video comments
   - Posting comments

## Live Chat Example

For YouTube live chat operations (which you mentioned in your requirements):

```java
// Full access for live chat (requires streamer/moderator)
var liveChatToken = oauthToken("LiveChat-Bot", "livechat-key",
        "https://www.googleapis.com/auth/youtube.force-ssl"
).credential("streamer@gmail.com");

var youtube = YouTubeService.youtube(liveChatToken);

// Get active live broadcast
var broadcasts = youtube.liveBroadcasts()
        .list(List.of("snippet"))
        .setBroadcastStatus("active")
        .setMine(true)
        .execute();

String liveChatId = broadcasts.getItems().get(0).getSnippet().getLiveChatId();

// Read chat messages
var messages = youtube.liveChatMessages()
        .list(liveChatId, List.of("snippet", "authorDetails"))
        .execute();

for (var msg : messages.getItems()) {
    System.out.println(msg.getAuthorDetails().getDisplayName() + ": "
            + msg.getSnippet().getDisplayMessage());
}

// Post a message
LiveChatMessage message = new LiveChatMessage();
LiveChatMessageSnippet snippet = new LiveChatMessageSnippet();
snippet.setLiveChatId(liveChatId);
snippet.setType("textMessageEvent");

LiveChatTextMessageDetails textDetails = new LiveChatTextMessageDetails();
textDetails.setMessageText("Hello from the bot!");
snippet.setTextMessageDetails(textDetails);
message.setSnippet(snippet);

youtube.liveChatMessages()
        .insert(List.of("snippet"), message)
        .execute();
```

## Important Notes

### OAuth Requirements
- **Live chat access**: Requires user to be the stream owner or a moderator
- **Service accounts**: Not supported by YouTube Data API
- **Incremental authorization**: Not supported for installed apps/devices

### Rate Limiting
- YouTube Data API has quota limits (10,000 units/day by default)
- Different operations consume different quota amounts
- Consider implementing additional rate limiting on top of built-in backoff

### Authentication
- All requests require either API key or OAuth 2.0 token
- Modification operations require OAuth authorization
- Private data access requires OAuth authorization

## OAuth Credentials Setup

Credentials are stored in: `<userhome>/uskoag/gdrive_gdocs_auth/<email>/credentials.json`

Each email account folder should contain:
- `credentials.json` - OAuth client credentials from Google Cloud Console
- `tokens_<hashed-app-key>/` - Auto-generated encrypted token storage

For YouTube API:
1. Go to Google Cloud Console
2. Enable YouTube Data API v3
3. Create OAuth 2.0 credentials (Desktop app or Web app)
4. Download credentials.json
5. Place in the appropriate directory

## Dependencies

This module depends on:
- `uskoag-gservices-oauth` - OAuth token management
- `google-api-services-youtube` - YouTube Data API v3 client
- `google-api-client` - Google API client library

## Security

OAuth tokens are automatically encrypted at rest using AES-256-GCM. The app-key is used to derive the encryption key, providing protection against credential theft. Each application should use a unique app-key.

## Further Reading

- [YouTube Data API v3 Documentation](https://developers.google.com/youtube/v3)
- [Live Streaming API](https://developers.google.com/youtube/v3/live)
- [OAuth 2.0 for YouTube](https://developers.google.com/youtube/v3/guides/authentication)
