package examples;

import uskoag.gservices.OAuthToken;
import uskoag.gservices.YouTubeService;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import static uskoag.gservices.OAuthToken.oauthToken;

/**
 * Comprehensive examples for YouTube Data API v3 operations.
 *
 * Available OAuth scopes:
 * - https://www.googleapis.com/auth/youtube (full access)
 * - https://www.googleapis.com/auth/youtube.readonly (read-only)
 * - https://www.googleapis.com/auth/youtube.force-ssl (full access with SSL)
 * - https://www.googleapis.com/auth/youtube.upload (upload only)
 * - https://www.googleapis.com/auth/youtubepartner (partner access)
 * - https://www.googleapis.com/auth/youtube.channel-memberships.creator (memberships)
 */
public class YouTubeExamples {

    // ============================================================================
    // 1. LIVE CHAT OPERATIONS
    // ============================================================================

    /**
     * Example: Read live chat messages from a stream.
     * Requires: OAuth with youtube or youtube.force-ssl scope
     * User must be: Stream owner or moderator
     */
    public static void readLiveChatMessages() throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("LiveChat-Reader", "livechat-readonly-key",
                "https://www.googleapis.com/auth/youtube.readonly"
        ).credential("streamer@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        // First, get the active live chat ID from a live broadcast
        String liveChatId = getActiveLiveChatId(youtube);

        // List messages
        YouTube.LiveChatMessages.List request = youtube.liveChatMessages()
                .list(liveChatId, List.of("snippet", "authorDetails"));

        LiveChatMessageListResponse response = request.execute();

        for (LiveChatMessage message : response.getItems()) {
            String author = message.getAuthorDetails().getDisplayName();
            String text = message.getSnippet().getDisplayMessage();
            System.out.println(author + ": " + text);
        }

        // For continuous polling, use the nextPageToken and pollingIntervalMillis
        String nextPageToken = response.getNextPageToken();
        Long pollingInterval = response.getPollingIntervalMillis();
    }

    /**
     * Example: Post a message to live chat.
     * Requires: OAuth with youtube or youtube.force-ssl scope
     * User must be: Stream owner or moderator
     */
    public static void postLiveChatMessage() throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("LiveChat-Bot", "livechat-bot-key",
                "https://www.googleapis.com/auth/youtube.force-ssl"
        ).credential("streamer@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);
        String liveChatId = getActiveLiveChatId(youtube);

        // Create the message
        LiveChatMessage message = new LiveChatMessage();
        LiveChatMessageSnippet snippet = new LiveChatMessageSnippet();
        snippet.setLiveChatId(liveChatId);
        snippet.setType("textMessageEvent");

        LiveChatTextMessageDetails textMessageDetails = new LiveChatTextMessageDetails();
        textMessageDetails.setMessageText("Hello from the bot!");
        snippet.setTextMessageDetails(textMessageDetails);

        message.setSnippet(snippet);

        // Insert the message
        YouTube.LiveChatMessages.Insert request = youtube.liveChatMessages()
                .insert(List.of("snippet"), message);
        LiveChatMessage response = request.execute();

        System.out.println("Message posted: " + response.getSnippet().getDisplayMessage());
    }

    /**
     * Example: Delete a live chat message (moderation).
     * Requires: OAuth with youtube or youtube.force-ssl scope
     * User must be: Stream owner or moderator
     */
    public static void deleteLiveChatMessage(String messageId) throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("LiveChat-Moderator", "livechat-mod-key",
                "https://www.googleapis.com/auth/youtube.force-ssl"
        ).credential("moderator@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        youtube.liveChatMessages().delete(messageId).execute();
        System.out.println("Message deleted: " + messageId);
    }

    // ============================================================================
    // 2. VIDEO OPERATIONS
    // ============================================================================

    /**
     * Example: Search for videos.
     * Requires: OAuth with youtube.readonly scope (or API key for public data)
     */
    public static void searchVideos(String query) throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Video-Search-App", "search-readonly-key",
                "https://www.googleapis.com/auth/youtube.readonly"
        ).credential("user@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        YouTube.Search.List search = youtube.search().list(List.of("snippet"));
        search.setQ(query);
        search.setType(List.of("video"));
        search.setMaxResults(25L);

        SearchListResponse searchResponse = search.execute();

        for (SearchResult result : searchResponse.getItems()) {
            System.out.println("Title: " + result.getSnippet().getTitle());
            System.out.println("Video ID: " + result.getId().getVideoId());
            System.out.println("Channel: " + result.getSnippet().getChannelTitle());
            System.out.println("---");
        }
    }

    /**
     * Example: Get video details including statistics.
     * Requires: OAuth with youtube.readonly scope
     */
    public static void getVideoDetails(String videoId) throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Video-Details-App", "video-readonly-key",
                "https://www.googleapis.com/auth/youtube.readonly"
        ).credential("user@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        YouTube.Videos.List request = youtube.videos()
                .list(List.of("snippet", "statistics", "contentDetails"));
        request.setId(List.of(videoId));

        VideoListResponse response = request.execute();

        if (!response.getItems().isEmpty()) {
            Video video = response.getItems().get(0);
            System.out.println("Title: " + video.getSnippet().getTitle());
            System.out.println("Views: " + video.getStatistics().getViewCount());
            System.out.println("Likes: " + video.getStatistics().getLikeCount());
            System.out.println("Duration: " + video.getContentDetails().getDuration());
        }
    }

    /**
     * Example: Upload a video.
     * Requires: OAuth with youtube.upload or youtube scope
     */
    public static void uploadVideo(String filePath, String title, String description)
            throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Video-Uploader", "upload-key",
                "https://www.googleapis.com/auth/youtube.upload"
        ).credential("uploader@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        // Create video metadata
        Video video = new Video();
        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle(title);
        snippet.setDescription(description);
        snippet.setTags(List.of("tag1", "tag2"));
        snippet.setCategoryId("22"); // People & Blogs
        video.setSnippet(snippet);

        VideoStatus status = new VideoStatus();
        status.setPrivacyStatus("private"); // private, public, or unlisted
        video.setStatus(status);

        // Upload the video file
        java.io.File videoFile = new java.io.File(filePath);
        com.google.api.client.http.InputStreamContent mediaContent =
            new com.google.api.client.http.InputStreamContent(
                "video/*",
                new java.io.FileInputStream(videoFile)
            );

        YouTube.Videos.Insert request = youtube.videos()
                .insert(List.of("snippet", "status"), video, mediaContent);

        Video uploadedVideo = request.execute();
        System.out.println("Video uploaded: " + uploadedVideo.getId());
    }

    /**
     * Example: Update video metadata.
     * Requires: OAuth with youtube scope
     */
    public static void updateVideoMetadata(String videoId) throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Video-Editor", "edit-key",
                "https://www.googleapis.com/auth/youtube"
        ).credential("owner@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        // First, get the existing video
        YouTube.Videos.List listRequest = youtube.videos()
                .list(List.of("snippet", "status"));
        listRequest.setId(List.of(videoId));
        Video video = listRequest.execute().getItems().get(0);

        // Update the metadata
        video.getSnippet().setTitle("Updated Title");
        video.getSnippet().setDescription("Updated description");

        // Send the update
        YouTube.Videos.Update updateRequest = youtube.videos()
                .update(List.of("snippet"), video);
        Video updatedVideo = updateRequest.execute();

        System.out.println("Video updated: " + updatedVideo.getSnippet().getTitle());
    }

    // ============================================================================
    // 3. CHANNEL OPERATIONS
    // ============================================================================

    /**
     * Example: Get channel information.
     * Requires: OAuth with youtube.readonly scope
     */
    public static void getChannelInfo() throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Channel-Info-App", "channel-readonly-key",
                "https://www.googleapis.com/auth/youtube.readonly"
        ).credential("user@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        YouTube.Channels.List request = youtube.channels()
                .list(List.of("snippet", "statistics", "contentDetails"));
        request.setMine(true); // Get authenticated user's channel

        ChannelListResponse response = request.execute();

        if (!response.getItems().isEmpty()) {
            Channel channel = response.getItems().get(0);
            System.out.println("Channel: " + channel.getSnippet().getTitle());
            System.out.println("Subscribers: " + channel.getStatistics().getSubscriberCount());
            System.out.println("Videos: " + channel.getStatistics().getVideoCount());
        }
    }

    /**
     * Example: Subscribe to a channel.
     * Requires: OAuth with youtube scope
     */
    public static void subscribeToChannel(String channelId) throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Subscription-Manager", "subscribe-key",
                "https://www.googleapis.com/auth/youtube"
        ).credential("user@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        Subscription subscription = new Subscription();
        SubscriptionSnippet snippet = new SubscriptionSnippet();
        ResourceId resourceId = new ResourceId();
        resourceId.setChannelId(channelId);
        resourceId.setKind("youtube#channel");
        snippet.setResourceId(resourceId);
        subscription.setSnippet(snippet);

        YouTube.Subscriptions.Insert request = youtube.subscriptions()
                .insert(List.of("snippet"), subscription);
        Subscription response = request.execute();

        System.out.println("Subscribed to: " + response.getSnippet().getTitle());
    }

    // ============================================================================
    // 4. PLAYLIST OPERATIONS
    // ============================================================================

    /**
     * Example: Create a playlist.
     * Requires: OAuth with youtube scope
     */
    public static void createPlaylist(String title, String description)
            throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Playlist-Manager", "playlist-key",
                "https://www.googleapis.com/auth/youtube"
        ).credential("user@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        Playlist playlist = new Playlist();
        PlaylistSnippet snippet = new PlaylistSnippet();
        snippet.setTitle(title);
        snippet.setDescription(description);
        playlist.setSnippet(snippet);

        PlaylistStatus status = new PlaylistStatus();
        status.setPrivacyStatus("private");
        playlist.setStatus(status);

        YouTube.Playlists.Insert request = youtube.playlists()
                .insert(List.of("snippet", "status"), playlist);
        Playlist response = request.execute();

        System.out.println("Playlist created: " + response.getId());
    }

    /**
     * Example: Add video to playlist.
     * Requires: OAuth with youtube scope
     */
    public static void addVideoToPlaylist(String playlistId, String videoId)
            throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Playlist-Manager", "playlist-key",
                "https://www.googleapis.com/auth/youtube"
        ).credential("user@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        PlaylistItem playlistItem = new PlaylistItem();
        PlaylistItemSnippet snippet = new PlaylistItemSnippet();
        snippet.setPlaylistId(playlistId);
        ResourceId resourceId = new ResourceId();
        resourceId.setKind("youtube#video");
        resourceId.setVideoId(videoId);
        snippet.setResourceId(resourceId);
        playlistItem.setSnippet(snippet);

        YouTube.PlaylistItems.Insert request = youtube.playlistItems()
                .insert(List.of("snippet"), playlistItem);
        PlaylistItem response = request.execute();

        System.out.println("Video added to playlist: " + response.getId());
    }

    // ============================================================================
    // 5. COMMENT OPERATIONS
    // ============================================================================

    /**
     * Example: Get comments for a video.
     * Requires: OAuth with youtube.readonly scope
     */
    public static void getVideoComments(String videoId) throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Comment-Reader", "comment-readonly-key",
                "https://www.googleapis.com/auth/youtube.readonly"
        ).credential("user@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        YouTube.CommentThreads.List request = youtube.commentThreads()
                .list(List.of("snippet"));
        request.setVideoId(videoId);
        request.setMaxResults(50L);
        request.setTextFormat("plainText");

        CommentThreadListResponse response = request.execute();

        for (CommentThread thread : response.getItems()) {
            Comment topComment = thread.getSnippet().getTopLevelComment();
            System.out.println("Author: " + topComment.getSnippet().getAuthorDisplayName());
            System.out.println("Comment: " + topComment.getSnippet().getTextDisplay());
            System.out.println("Likes: " + topComment.getSnippet().getLikeCount());
            System.out.println("---");
        }
    }

    /**
     * Example: Post a comment on a video.
     * Requires: OAuth with youtube.force-ssl scope
     */
    public static void postComment(String videoId, String commentText)
            throws IOException, GeneralSecurityException {
        var oauthToken = oauthToken("Comment-Poster", "comment-key",
                "https://www.googleapis.com/auth/youtube.force-ssl"
        ).credential("user@gmail.com");

        var youtube = YouTubeService.youtube(oauthToken);

        CommentThread commentThread = new CommentThread();
        CommentThreadSnippet snippet = new CommentThreadSnippet();
        Comment topLevelComment = new Comment();
        CommentSnippet commentSnippet = new CommentSnippet();
        commentSnippet.setTextOriginal(commentText);
        commentSnippet.setVideoId(videoId);
        topLevelComment.setSnippet(commentSnippet);
        snippet.setTopLevelComment(topLevelComment);
        commentThread.setSnippet(snippet);

        YouTube.CommentThreads.Insert request = youtube.commentThreads()
                .insert(List.of("snippet"), commentThread);
        CommentThread response = request.execute();

        System.out.println("Comment posted: " + response.getId());
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Helper: Get active live chat ID for the authenticated user's stream.
     */
    private static String getActiveLiveChatId(YouTube youtube) throws IOException {
        YouTube.LiveBroadcasts.List request = youtube.liveBroadcasts()
                .list(List.of("snippet"));
        request.setBroadcastStatus("active");
        request.setMine(true);

        LiveBroadcastListResponse response = request.execute();

        if (response.getItems().isEmpty()) {
            throw new IllegalStateException("No active live broadcast found");
        }

        return response.getItems().get(0).getSnippet().getLiveChatId();
    }

    /**
     * Example: Multiple operations with different scopes.
     * Shows how to use different OAuth tokens for different operations.
     */
    public static void multipleOperationsExample() throws IOException, GeneralSecurityException {
        // Read-only operations
        var readToken = oauthToken("My-YouTube-App", "readonly-key",
                "https://www.googleapis.com/auth/youtube.readonly"
        ).credential("user@gmail.com");

        // Full access operations
        var writeToken = oauthToken("My-YouTube-App", "write-key",
                "https://www.googleapis.com/auth/youtube"
        ).credential("user@gmail.com");

        var youtubeRead = YouTubeService.youtube(readToken);
        var youtubeWrite = YouTubeService.youtube(writeToken);

        // Use youtubeRead for searches, getting info, etc.
        // Use youtubeWrite for uploads, updates, deletes, etc.
    }
}
