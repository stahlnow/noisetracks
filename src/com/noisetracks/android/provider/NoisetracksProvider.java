package com.noisetracks.android.provider;


import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.provider.NoisetracksContract;
import com.noisetracks.android.provider.NoisetracksContract.Entries;
import com.noisetracks.android.provider.NoisetracksContract.Profiles;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

/**
 * Provides access to a database of entries. Each entry has a title, the entry
 * itself, a creation date and a modified data.
 */

public class NoisetracksProvider extends ContentProvider {
    // Used for debugging and logging
    private static final String TAG = "NoisetracksProvider";

    /**
     * The database that the provider uses as its underlying data store
     */
    public static final String DATABASE_NAME = "noisetracks.db";

    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 1;

    /**
     * Projection maps used to select columns from the database
     */
    private static HashMap<String, String> sEntriesProjectionMap;
    private static HashMap<String, String> sProfilesProjectionMap;

    
    /**
     * Standard projection for the columns of a entry.
     */
    public static final String[] READ_ENTRY_PROJECTION = new String[] {
        Entries._ID,
        Entries.COLUMN_NAME_FILENAME,
        Entries.COLUMN_NAME_SPECTROGRAM,
        Entries.COLUMN_NAME_CREATED,
        Entries.COLUMN_NAME_LATITUDE,
        Entries.COLUMN_NAME_LONGITUDE,
        Entries.COLUMN_NAME_RECORDED,
        Entries.COLUMN_NAME_RESOURCE_URI,
        Entries.COLUMN_NAME_MUGSHOT,
        Entries.COLUMN_NAME_USERNAME,
        Entries.COLUMN_NAME_UUID,
        Entries.COLUMN_NAME_TYPE,
        Entries.COLUMN_NAME_SCORE,
        Entries.COLUMN_NAME_VOTE
    };
    
    /*
    private static final int READ_ENTRY_FILENAME_INDEX = 1;
    private static final int READ_ENTRY_LATITUDE_INDEX = 4;
    private static final int READ_ENTRY_LONGITUDE_INDEX = 5;
    private static final int READ_ENTRY_RECORDED_INDEX = 6;
    */
    
    /**
     * Standard projection for the columns of a profile.
     */
    public static final String[] READ_PROFILE_PROJECTION = new String[] {
    	Profiles._ID,
    	Profiles.COLUMN_NAME_USERNAME,
    	Profiles.COLUMN_NAME_MUGSHOT,
    	Profiles.COLUMN_NAME_BIO,
    	Profiles.COLUMN_NAME_NAME,
    	Profiles.COLUMN_NAME_TRACKS,
        Profiles.COLUMN_NAME_WEBSITE,
        Profiles.COLUMN_NAME_EMAIL
    };
    
    /*
     * Constants used by the Uri matcher to choose an action based on the pattern
     * of the incoming URI
     */

    private static final int ENTRIES = 1;	    // The incoming URI matches the Entries URI pattern
    private static final int ENTRY_ID = 2;	    // The incoming URI matches the Entry ID URI pattern
    private static final int PROFILES = 3;	    // The incoming URI matches the Profiles URI pattern
    private static final int PROFILE_ID = 4;	    // The incoming URI matches the Profile ID URI pattern

    /**
     * A UriMatcher instance
     */
    private static final UriMatcher sUriMatcher;

    // Handle to a new DatabaseHelper.
    private DatabaseHelper mOpenHelper;


    /**
     * A block that instantiates and sets static objects
     */
    static {

        /*
         * Creates and initializes the URI matcher
         */
        // Create a new instance
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // Add a pattern that routes URIs terminated with "entries" to a ENTRIES operation
        sUriMatcher.addURI(NoisetracksContract.AUTHORITY, "entries", ENTRIES);

        // Add a pattern that routes URIs terminated with "entries" plus an integer
        // to a entry ID operation
        sUriMatcher.addURI(NoisetracksContract.AUTHORITY, "entries/#", ENTRY_ID);

        // Add a pattern that routes URIs terminated with "profiles" to a PROFILES operation
        sUriMatcher.addURI(NoisetracksContract.AUTHORITY, "profiles", PROFILES);

        // Add a pattern that routes URIs terminated with "profiles" plus an integer
        // to a profile ID operation
        sUriMatcher.addURI(NoisetracksContract.AUTHORITY, "profiles/#", PROFILE_ID);

        
        /*
         * Creates and initializes projection map for each table that returns all columns
         */
        
        // Creates new projection map instances. The maps return a column name
        // given a string. The two are usually equal.
        sEntriesProjectionMap = new HashMap<String, String>();

        sEntriesProjectionMap.put(Entries._ID, Entries._ID);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_FILENAME, Entries.COLUMN_NAME_FILENAME);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_SPECTROGRAM, Entries.COLUMN_NAME_SPECTROGRAM);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_CREATED, Entries.COLUMN_NAME_CREATED);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_LATITUDE, Entries.COLUMN_NAME_LATITUDE);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_LONGITUDE, Entries.COLUMN_NAME_LONGITUDE);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_RECORDED, Entries.COLUMN_NAME_RECORDED);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_RESOURCE_URI, Entries.COLUMN_NAME_RESOURCE_URI);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_MUGSHOT, Entries.COLUMN_NAME_MUGSHOT);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_USERNAME, Entries.COLUMN_NAME_USERNAME);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_UUID, Entries.COLUMN_NAME_UUID);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_TYPE, Entries.COLUMN_NAME_TYPE);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_SCORE, Entries.COLUMN_NAME_SCORE);
        sEntriesProjectionMap.put(Entries.COLUMN_NAME_VOTE, Entries.COLUMN_NAME_VOTE);

        // Same for profiles table.
        sProfilesProjectionMap = new HashMap<String, String>();
        sProfilesProjectionMap.put(Profiles._ID, Profiles._ID);
        sProfilesProjectionMap.put(Profiles.COLUMN_NAME_USERNAME, Profiles.COLUMN_NAME_USERNAME);
        sProfilesProjectionMap.put(Profiles.COLUMN_NAME_MUGSHOT, Profiles.COLUMN_NAME_MUGSHOT);
        sProfilesProjectionMap.put(Profiles.COLUMN_NAME_BIO, Profiles.COLUMN_NAME_BIO);
        sProfilesProjectionMap.put(Profiles.COLUMN_NAME_NAME, Profiles.COLUMN_NAME_NAME);
        sProfilesProjectionMap.put(Profiles.COLUMN_NAME_TRACKS, Profiles.COLUMN_NAME_TRACKS);
        sProfilesProjectionMap.put(Profiles.COLUMN_NAME_WEBSITE, Profiles.COLUMN_NAME_WEBSITE);
        sProfilesProjectionMap.put(Profiles.COLUMN_NAME_EMAIL, Profiles.COLUMN_NAME_EMAIL);
    }

    /**
    *
    * This class helps open, create, and upgrade the database file. Set to package visibility
    * for testing purposes.
    */
   static class DatabaseHelper extends SQLiteOpenHelper {

       DatabaseHelper(Context context) {
           // calls the super constructor, requesting the default cursor factory.
           super(context, DATABASE_NAME, null, DATABASE_VERSION);
       }

       /**
        *
        * Creates the underlying database with table name and column names taken from the
        * NoisetracksContract class.
        */
       @Override
       public void onCreate(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE " + Entries.TABLE_NAME + " ("
                   + Entries._ID + " INTEGER PRIMARY KEY,"
                   + Entries.COLUMN_NAME_FILENAME + " TEXT,"
                   + Entries.COLUMN_NAME_SPECTROGRAM + " TEXT,"
                   + Entries.COLUMN_NAME_CREATED + " TEXT,"
                   + Entries.COLUMN_NAME_LATITUDE + " REAL,"
                   + Entries.COLUMN_NAME_LONGITUDE + " REAL,"
                   + Entries.COLUMN_NAME_RECORDED + " TEXT,"
                   + Entries.COLUMN_NAME_RESOURCE_URI + " TEXT,"
                   + Entries.COLUMN_NAME_MUGSHOT + " TEXT,"
                   + Entries.COLUMN_NAME_USERNAME + " TEXT,"
                   + Entries.COLUMN_NAME_UUID + " TEXT,"
                   + Entries.COLUMN_NAME_TYPE + " INTEGER,"
                   + Entries.COLUMN_NAME_SCORE + " INTEGER,"
                   + Entries.COLUMN_NAME_VOTE + " INTEGER,"
                   + "UNIQUE(" + Entries.COLUMN_NAME_UUID + ")" //  ON CONFLICT REPLACE see insert(...)
                   + ");");
           
           db.execSQL("CREATE TABLE " + Profiles.TABLE_NAME + " ("
                   + Profiles._ID + " INTEGER PRIMARY KEY,"
                   + Profiles.COLUMN_NAME_USERNAME + " TEXT,"
                   + Profiles.COLUMN_NAME_MUGSHOT + " TEXT,"
                   + Profiles.COLUMN_NAME_BIO + " TEXT,"
                   + Profiles.COLUMN_NAME_NAME + " TEXT,"
                   + Profiles.COLUMN_NAME_TRACKS + " INT,"
                   + Profiles.COLUMN_NAME_WEBSITE + " TEXT,"
                   + Profiles.COLUMN_NAME_EMAIL + " TEXT,"
                   + "UNIQUE(" + Entries.COLUMN_NAME_USERNAME + ")" //  ON CONFLICT REPLACE see insert(...)
                   + ");");
       }

       /**
        *
        * Demonstrates that the provider must consider what happens when the
        * underlying datastore is changed. In this sample, the database is upgraded the database
        * by destroying the existing data.
        * A real application should upgrade the database in place.
        */
       @Override
       public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

           // Logs that the database is being upgraded
           Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                   + newVersion + ", which will destroy all old data");

           // Kills the tables and existing data
           db.execSQL("DROP TABLE IF EXISTS " + Entries.TABLE_NAME);
           db.execSQL("DROP TABLE IF EXISTS " + Profiles.TABLE_NAME);

           // Recreates the database with a new version
           onCreate(db);
       }
   }

   /**
    *
    * Initializes the provider by creating a new DatabaseHelper. onCreate() is called
    * automatically when Android creates the provider in response to a resolver request from a
    * client.
    */
   @Override
   public boolean onCreate() {

       // Creates a new helper object. Note that the database itself isn't opened until
       // something tries to access it, and it's only created if it doesn't already exist.
       mOpenHelper = new DatabaseHelper(getContext());

       // Assumes that any failures will be reported by a thrown exception.
       return true;
   }

   /**
    * This method is called when a client calls
    * {@link android.content.ContentResolver#query(Uri, String[], String, String[], String)}.
    * Queries the database and returns a cursor containing the results.
    *
    * @return A cursor containing the results of the query. The cursor exists but is empty if
    * the query returns no results or an exception occurs.
    * @throws IllegalArgumentException if the incoming URI pattern is invalid.
    */
   @Override
   public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
           String sortOrder) {

       // Constructs a new query builder and sets its table name
       SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

       /**
        * Choose the projection and adjust the "where" clause based on URI pattern-matching.
        */
       switch (sUriMatcher.match(uri)) {
           // If the incoming URI is for entries, chooses the Entries projection
           case ENTRIES:
        	   qb.setTables(Entries.TABLE_NAME);
               qb.setProjectionMap(sEntriesProjectionMap);
               break;

           /* If the incoming URI is for a single entry identified by its ID, chooses the
            * entry ID projection, and appends "_ID = <trackID>" to the where clause, so that
            * it selects that single entry
            */
           case ENTRY_ID:
        	   qb.setTables(Entries.TABLE_NAME);
               qb.setProjectionMap(sEntriesProjectionMap);
               qb.appendWhere(
                   Entries._ID +    // the name of the ID column
                   "=" +
                   // the position of the entry ID itself in the incoming URI
                   uri.getPathSegments().get(Entries.ENTRY_ID_PATH_POSITION));
               break;
               
           case PROFILES:
        	   qb.setTables(Profiles.TABLE_NAME);
               qb.setProjectionMap(sProfilesProjectionMap);
               break;
           case PROFILE_ID:
        	   qb.setTables(Profiles.TABLE_NAME);
               qb.setProjectionMap(sProfilesProjectionMap);
               qb.appendWhere(Profiles._ID + "=" + uri.getPathSegments().get(Profiles.PROFILE_ID_PATH_POSITION));
               break;

           default:
               // If the URI doesn't match any of the known patterns, throw an exception.
               throw new IllegalArgumentException("Unknown URI " + uri);
       }


       String orderBy = "";
       // If no sort order is specified, uses the default
       if (TextUtils.isEmpty(sortOrder)) {
    	   switch (sUriMatcher.match(uri)) {
    	   case ENTRIES:
    	   case ENTRY_ID:
    		   orderBy = Entries.DEFAULT_SORT_ORDER;
    		   break;
    	   case PROFILES:
    	   case PROFILE_ID:
    		   orderBy = Profiles.DEFAULT_SORT_ORDER;
    		   break;
    	   }
           
       } else {
           // otherwise, uses the incoming sort order
           orderBy = sortOrder;
       }

       // Opens the database object in "read" mode, since no writes need to be done.
       SQLiteDatabase db = mOpenHelper.getReadableDatabase();

       /*
        * Performs the query. If no problems occur trying to read the database, then a Cursor
        * object is returned; otherwise, the cursor variable contains null. If no records were
        * selected, then the Cursor object is empty, and Cursor.getCount() returns 0.
        */
       Cursor c = qb.query(
           db,            // The database to query
           projection,    // The columns to return from the query
           selection,     // The columns for the where clause
           selectionArgs, // The values for the where clause
           null,          // don't group the rows
           null,          // don't filter by row groups
           orderBy        // The sort order
       );

       // Tells the Cursor what URI to watch, so it knows when its source data changes
       c.setNotificationUri(getContext().getContentResolver(), uri);
       return c;
   }

   /**
    * This is called when a client calls {@link android.content.ContentResolver#getType(Uri)}.
    * Returns the MIME data type of the URI given as a parameter.
    *
    * @param uri The URI whose MIME type is desired.
    * @return The MIME type of the URI.
    * @throws IllegalArgumentException if the incoming URI pattern is invalid.
    */
   @Override
   public String getType(Uri uri) {

       /**
        * Chooses the MIME type based on the incoming URI pattern
        */
       switch (sUriMatcher.match(uri)) {

           // If the pattern is for entries or live folders, returns the general content type.
           case ENTRIES:
               return Entries.CONTENT_TYPE;

           // If the pattern is for entry IDs, returns the entry ID content type.
           case ENTRY_ID:
               return Entries.CONTENT_ITEM_TYPE;
               
           case PROFILES:
               return Profiles.CONTENT_TYPE;
               
           case PROFILE_ID:
               return Profiles.CONTENT_ITEM_TYPE;

           // If the URI pattern doesn't match any permitted patterns, throws an exception.
           default:
               throw new IllegalArgumentException("Unknown URI " + uri);
       }
    }


    /**
     * Returns the types of available data streams.  URIs to specific entries are supported.
     * The application can convert such a entry to a audio/wave stream.
     *
     * @param uri the URI to analyze
     * @param mimeTypeFilter The MIME type to check for. This method only returns a data stream
     * type for MIME types that match the filter. No MIME types are supported yet.
     * @return a data stream MIME type. Currently, null is returned.
     * @throws IllegalArgumentException if the URI pattern doesn't match any supported patterns.
     */
    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        /**
         *  Chooses the data stream type based on the incoming URI pattern.
         */
        switch (sUriMatcher.match(uri)) {

            // If the pattern is for entries or profiles, return null. Data streams are not
            // supported for this type of URI.
            case ENTRIES:
            case PROFILES:
            case PROFILE_ID:
                return null;

            // If the pattern is for entry IDs and the MIME filter is audio/wave, then return
            // audio/wave TODO: not implemented
            case ENTRY_ID:
                return null;

                // If the URI pattern doesn't match any permitted patterns, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
    }


    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#insert(Uri, ContentValues)}.
     * Inserts a new row into the database. This method sets up default values for any
     * columns that are not included in the incoming map.
     * If rows were inserted, then listeners are notified of the change.
     * @return The row ID of the inserted row.
     * @throws SQLException if the insertion fails.
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        // A map to hold the new record's values.
        ContentValues values;
        
        // If the incoming values map is not null, uses it for the new values.
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
        	throw new IllegalArgumentException("Wrong ContentValues");
        }
        
        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

    	switch (sUriMatcher.match(uri)) {
    	case ENTRIES:
    		if (values.containsKey(Entries.COLUMN_NAME_LATITUDE) == false) {
            	values.put(Entries.COLUMN_NAME_LATITUDE, 0.0);
            }
            if (values.containsKey(Entries.COLUMN_NAME_LONGITUDE) == false) {
            	values.put(Entries.COLUMN_NAME_LONGITUDE, 0.0);
            }
            // If the values map doesn't contain entry recording date, sets the value to 'now'.
            if (values.containsKey(Entries.COLUMN_NAME_RECORDED) == false) {
                values.put(Entries.COLUMN_NAME_RECORDED, NoisetracksApplication.SDF.format(new Date()));
            }
            if (values.containsKey(Entries.COLUMN_NAME_CREATED) == false) {
                values.put(Entries.COLUMN_NAME_CREATED, NoisetracksApplication.SDF.format(new Date()));
            }
            // If the values map doesn't contain 'type', set it to DOWNLOADED by default
            // Entries created by the user must have set it to RECORDED or TRACKED.
            if (values.containsKey(Entries.COLUMN_NAME_TYPE) == false) {
                values.put(Entries.COLUMN_NAME_TYPE, Entries.TYPE.DOWNLOADED.ordinal());
            }
            if (values.containsKey(Entries.COLUMN_NAME_USERNAME) == false) {
            	values.put(Entries.COLUMN_NAME_USERNAME, "");
            }
            if (values.containsKey(Entries.COLUMN_NAME_UUID) == false) {
            	values.put(Entries.COLUMN_NAME_UUID, UUID.randomUUID().toString());
            }
            
            // Performs the insert and returns the ID of the new entry.
            long rowId = db.insertWithOnConflict(
                Entries.TABLE_NAME,        	// The table to insert into.
                Entries.COLUMN_NAME_FILENAME,	// A hack, SQLite sets this column value to null, if values is empty.
                values,                           				// A map of column names, and the values to insert into the columns.
                SQLiteDatabase.CONFLICT_REPLACE					// Replace items that have already been inserted.
            );

            // If the insert succeeded, the row ID exists.
            if (rowId > 0) {
                // Creates a URI with the entry ID pattern and the new row ID appended to it.
                Uri trackUri = ContentUris.withAppendedId(Entries.CONTENT_ID_URI_BASE, rowId);
                // Notifies observers registered against this provider that the data changed.               
                getContext().getContentResolver().notifyChange(trackUri, null, false);
                return trackUri;
            }

    		break;
    	case PROFILES:
    		// Performs the insert and returns the ID of the new entry.
            long rowIdProfile = db.insertWithOnConflict(
                Profiles.TABLE_NAME,        	// The table to insert into.
                Profiles.COLUMN_NAME_USERNAME,	// A hack, SQLite sets this column value to null, if values is empty.
                values,                           				// A map of column names, and the values to insert into the columns.
                SQLiteDatabase.CONFLICT_REPLACE					// Replace items that have already been inserted.
            );
            // If the insert succeeded, the row ID exists.
            if (rowIdProfile > 0) {
                // Creates a URI with the entry ID pattern and the new row ID appended to it.
                Uri profileUri = ContentUris.withAppendedId(Profiles.CONTENT_ID_URI_BASE, rowIdProfile);
                // Notifies observers registered against this provider that the data changed.
                getContext().getContentResolver().notifyChange(profileUri, null, false);
                return profileUri;
            }
    		break;
    	default:
    		throw new IllegalArgumentException("Unknown URI " + uri);
    	}
        
        // If the insert didn't succeed, then the rowID is <= 0. Throws an exception.
        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#delete(Uri, String, String[])}.
     * Deletes records from the database. If the incoming URI matches the entry ID URI pattern,
     * this method deletes the one record specified by the ID in the URI. Otherwise, it deletes a
     * a set of records. The record or records must also match the input selection criteria
     * specified by where and whereArgs.
     *
     * If rows were deleted, then listeners are notified of the change.
     * @return If a "where" clause is used, the number of rows affected is returned, otherwise
     * 0 is returned. To delete all rows and get a row count, use "1" as the where clause.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

    	String filename = "";
    	Cursor c = query(uri, null, null, null, null);
		if (c != null) {
			if (c.moveToFirst()) {
				filename = c.getString(c.getColumnIndex(Entries.COLUMN_NAME_FILENAME));
			}
			c.close();
		}
    	
    	
        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;

        int count;

        // Does the delete based on the incoming URI pattern.
        switch (sUriMatcher.match(uri)) {

            // If the incoming pattern matches the general pattern for entries, does a delete
            // based on the incoming "where" columns and arguments.
            case ENTRIES:
                count = db.delete(
                    Entries.TABLE_NAME,  // The database table name
                    where,                     // The incoming where clause column names
                    whereArgs                  // The incoming where clause values
                );
                break;

                // If the incoming URI matches a single entry ID, does the delete based on the
                // incoming data, but modifies the where clause to restrict it to the
                // particular entry ID.
            case ENTRY_ID:
                /*
                 * Starts a final WHERE clause by restricting it to the
                 * desired entry ID.
                 */
                finalWhere =
                        Entries._ID +                              // The ID column name
                        " = " +                                          // test for equality
                        uri.getPathSegments().                           // the incoming entry ID
                            get(Entries.ENTRY_ID_PATH_POSITION)
                ;

                // If there were additional selection criteria, append them to the final
                // WHERE clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Performs the delete.
                count = db.delete(
                		Entries.TABLE_NAME,  // The database table name.
                    finalWhere,                // The final WHERE clause
                    whereArgs                  // The incoming where clause values.
                );
                break;

            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null, false);
        
        File file = new File(filename);
        if (file.exists()) {
        	boolean d = file.delete();
        	if (d) {
        		Log.v(TAG, "Removed file " + file.getName());
        	}
        }
        

        // Returns the number of rows deleted.
        return count;
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#update(Uri,ContentValues,String,String[])}
     * Updates records in the database. The column names specified by the keys in the values map
     * are updated with new data specified by the values in the map. If the incoming URI matches the
     * entry ID URI pattern, then the method updates the one record specified by the ID in the URI;
     * otherwise, it updates a set of records. The record or records must match the input
     * selection criteria specified by where and whereArgs.
     * If rows were updated, then listeners are notified of the change.
     *
     * @param uri The URI pattern to match and update.
     * @param values A map of column names (keys) and new values (values).
     * @param where An SQL "WHERE" clause that selects records based on their column values. If this
     * is null, then all records that match the URI pattern are selected.
     * @param whereArgs An array of selection criteria. If the "where" param contains value
     * place holders ("?"), then each placeholder is replaced by the corresponding element in the
     * array.
     * @return The number of rows updated.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        // Does the update based on the incoming URI pattern
        switch (sUriMatcher.match(uri)) {

            // If the incoming URI matches the general entries pattern, does the update based on
            // the incoming data.
            case ENTRIES:

                // Does the update and returns the number of rows updated.
                count = db.update(
                	Entries.TABLE_NAME, 	  // The database table name.
                    values,                   // A map of column names and new values to use.
                    where,                    // The where clause column names.
                    whereArgs                 // The where clause column values to select on.
                );
                break;

            // If the incoming URI matches a single entry ID, does the update based on the incoming
            // data, but modifies the where clause to restrict it to the particular entry ID.
            case ENTRY_ID:

                /*
                 * Starts creating the final WHERE clause by restricting it to the incoming
                 * entry ID.
                 */
                finalWhere =
                		Entries._ID +                  // The ID column name
                        " = " +                                          // test for equality
                        uri.getPathSegments().                           // the incoming entry ID
                            get(Entries.ENTRY_ID_PATH_POSITION)
                ;

                // If there were additional selection criteria, append them to the final WHERE
                // clause
                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }


                // Does the update and returns the number of rows updated.
                count = db.update(
                	Entries.TABLE_NAME, // The database table name.
                    values,                   // A map of column names and new values to use.
                    finalWhere,               // The final WHERE clause to use
                                              // place holders for whereArgs
                    whereArgs                 // The where clause column values to select on, or
                                              // null if the values are in the where argument.
                );
                break;
                
            case PROFILE_ID:

                finalWhere =
                		Profiles._ID +                  				// The ID column name
                        " = " +                                         // test for equality
                        uri.getPathSegments().                          // the incoming profile ID
                            get(Profiles.PROFILE_ID_PATH_POSITION)
                ;

                // If there were additional selection criteria, append them to the final WHERE clause
                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }


                // Does the update and returns the number of rows updated.
                count = db.update(
                	Profiles.TABLE_NAME,	  // The database table name.
                    values,                   // A map of column names and new values to use.
                    finalWhere,               // The final WHERE clause to use place holders for whereArgs
                    whereArgs                 // The where clause column values to select on, or null if the values are in the where argument.
                );
                break;
                
            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /* Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null, true);

        // Returns the number of rows updated.
        return count;
    }

    /**
     * A test package can call this to get a handle to the database underlying NoisetracksProvider,
     * so it can insert test data into the database. The test case class is responsible for
     * instantiating the provider in a test context; {@link android.test.ProviderTestCase2} does
     * this during the call to setUp()
     *
     * @return a handle to the database helper object for the provider's data.
     */
    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}
