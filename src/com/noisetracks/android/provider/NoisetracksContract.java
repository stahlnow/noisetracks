package com.noisetracks.android.provider;

import android.net.Uri;
import android.provider.BaseColumns;

public final class NoisetracksContract {

	// this must match the provider authority defined in the Manifest.
	public static final String AUTHORITY = "com.noisetracks.android.provider";
	
	/**
     * Query parameter keys
     */
    public static final String QUERY_PARAMETER_LIMIT = "limit";
    
    // This class cannot be instantiated
    private NoisetracksContract() {
    }

    /**
     * Entries table contract
     */
    public static final class Entries implements BaseColumns {

        // This class cannot be instantiated
        private Entries() {}
        
        
        public enum TYPE {
        	DOWNLOADED,	 	// Entry was downloaded from REST api.
        	TRACKED,		// Entry was recorded / tracked in the background. Tracked entries will be processed and uploaded automatically. These entries don't show up anywhere.
        	RECORDED,		// Entry was manually recorded via the record menu. User has to manually upload. Theses entries show up only in profile view of logged in user.
        	UPLOADING,		// Entry is being uploaded
        	LOAD_MORE,		// Special entry to load more items from api
        }

        /**
         * The table name offered by this provider
         */
        public static final String TABLE_NAME = "entries";

        /*
         * URI definitions
         */

        /**
         * The scheme part for this provider's URI
         */
        private static final String SCHEME = "content://";

        /**
         * Path parts for the URIs
         */

        /**
         * Path part for the Tracks URI
         */
        private static final String PATH_ENTRIES = "/entries";

        /**
         * Path part for the Track ID URI
         */
        private static final String PATH_ENTRY_ID = "/entries/";

        /**
         * 0-relative position of a entry ID segment in the path part of a entry ID URI
         */
        public static final int ENTRY_ID_PATH_POSITION = 1;

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_ENTRIES);

        /**
         * The content URI base for a single entry. Callers must
         * append a numeric entry id to this Uri to retrieve a entry
         */
        public static final Uri CONTENT_ID_URI_BASE = Uri.parse(SCHEME + AUTHORITY + PATH_ENTRY_ID);

        /**
         * The content URI match pattern for a single entry, specified by its ID. Use this to match
         * incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN = Uri.parse(SCHEME + AUTHORITY + PATH_ENTRY_ID + "/#");

        /*
         * MIME type definitions
         */

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of entries.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.noisetracks.entry";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single
         * entry.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.noisetracks.entry";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "created DESC";
        

        /*
         * Column definitions
         */

        /**
         * Column name for the filename of the entry
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_FILENAME = "filename";
        
        /**
         * Column name for the spectrogram file
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_SPECTROGRAM = "spectrogram";

        /**
         * Column name for the date when the entry was uploaded/created on the server
         * <P>Type: TEXT (in the date format "yyyy-MM-dd'T'HH:mm:ss.SSS")</P>
         */
        public static final String COLUMN_NAME_CREATED = "created";
        
        /**
         * Column name for the date when the entry was recorded
         * <P>Type: TEXT (in the date format "yyyy-MM-dd'T'HH:mm:ss.SSS")</P>
         */
        public static final String COLUMN_NAME_RECORDED = "recorded";
        
        /**
         * Column name of the entry latitude
         * <P>Type: REAL</P>
         */
        public static final String COLUMN_NAME_LATITUDE = "latitude";
        
        /**
         * Column name of the entry longitude
         * <P>Type: REAL</P>
         */
        public static final String COLUMN_NAME_LONGITUDE = "longitude";
        
        /**
         * Column name for the resource uri
         * <P>Type: TEXT for example '/api/v1/entry/1/'</P>
         */
        public static final String COLUMN_NAME_RESOURCE_URI = "resource_uri";
        
        /**
         * Column that indicates if the entry has been uploaded (this is not mirrored on the web DB)
         * <P>Type: INTEGER</P>
         */
        public static final String COLUMN_NAME_TYPE = "type";
              
        /**
         * Column name for the mugshot URL
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_MUGSHOT = "mugshot";
        
        /**
         * Column name for the username
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_USERNAME = "username";
        
        /**
         * Column name for the uuid
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_UUID = "uuid";
        
        /**
         * Column name for the score
         * <P>Type: INTEGER</P>
         */
        public static final String COLUMN_NAME_SCORE = "score";
        
        /**
         * Column name for the vote: 0 = neutral, -1 = dislike, +1 = like
         * <P>Type: INTEGER</P>
         */
        public static final String COLUMN_NAME_VOTE = "vote";
        
        /**
         * Column that indicates if the entry should be looped
         * <P>Type: INTEGER</P>
         */
        public static final String COLUMN_NAME_LOOP = "loop";
        
        /**
         * Column for loop in point, default is 0.
         * <P>Type: INTEGER</P>
         */
        public static final String COLUMN_NAME_LOOP_START = "loopstart";
        
        /**
         * Column for loop out point, default is 0.
         * <P>Type: INTEGER</P>
         */
        public static final String COLUMN_NAME_LOOP_END = "loopend";
        
        /**
         * Column for pitch / playback rate in percent. Can be value of -100 to +100.
         * <P>Type: INTEGER</P>
         */
        public static final String COLUMN_NAME_PITCH = "pitch";
    }

    
    /**
     * Profiles table contract
     */
    public static final class Profiles implements BaseColumns {

        // This class cannot be instantiated
        private Profiles() {}

        /**
         * The table name offered by this provider
         */
        public static final String TABLE_NAME = "profiles";

        /*
         * URI definitions
         */

        /**
         * The scheme part for this provider's URI
         */
        private static final String SCHEME = "content://";

        /**
         * Path parts for the URIs
         */

        /**
         * Path part for the Profiles URI
         */
        private static final String PATH_PROFILES = "/profiles";

        /**
         * Path part for the Profile ID URI
         */
        private static final String PATH_PROFILE_ID = "/profiles/";

        /**
         * 0-relative position of a profile ID segment in the path part of a profile ID URI
         */
        public static final int PROFILE_ID_PATH_POSITION = 1;

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_PROFILES);

        /**
         * The content URI base for a single profile. Callers must
         * append a numeric profile id to this Uri to retrieve a profile
         */
        public static final Uri CONTENT_ID_URI_BASE = Uri.parse(SCHEME + AUTHORITY + PATH_PROFILE_ID);

        /**
         * The content URI match pattern for a single profile, specified by its ID. Use this to match
         * incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN = Uri.parse(SCHEME + AUTHORITY + PATH_PROFILE_ID + "/#");

        /*
         * MIME type definitions
         */

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of profiles.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.noisetracks.profile";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single
         * profile.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.noisetracks.profile";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "username DESC";
        

        /*
         * Column definitions
         */

        /**
         * Column name for the username of the profile
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_USERNAME = "username";
        
        /**
         * Column name for the mugshot URL
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_MUGSHOT = "mugshot";
        
        /**
         * Column name for the bio
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_BIO = "bio";
        
        /**
         * Column name for the name
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_NAME = "name";

        /**
         * Column name for the number of uploaded tracks
         * <P>Type: INTEGER	</P>
         */
        public static final String COLUMN_NAME_TRACKS = "tracks";
        
        /**
         * Column name for the website
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_WEBSITE = "website";
        
        /**
         * Column name for the e-mail, only visible to user
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_EMAIL = "email";
        
    }

}