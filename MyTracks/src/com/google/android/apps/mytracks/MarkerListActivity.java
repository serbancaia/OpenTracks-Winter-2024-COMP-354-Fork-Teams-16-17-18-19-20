/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.apps.mytracks;

import com.google.android.apps.mytracks.content.MyTracksProviderUtils;
import com.google.android.apps.mytracks.content.Waypoint;
import com.google.android.apps.mytracks.content.WaypointsColumns;
import com.google.android.apps.mytracks.fragments.DeleteOneMarkerDialogFragment;
import com.google.android.apps.mytracks.util.ApiAdapterFactory;
import com.google.android.apps.mytracks.util.PreferencesUtils;
import com.google.android.apps.mytracks.util.StringUtils;
import com.google.android.maps.mytracks.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.ResourceCursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Activity to show a list of markers in a track.
 *
 * @author Leif Hendrik Wilden
 */
public class MarkerListActivity extends FragmentActivity {
 
  public static final String EXTRA_TRACK_ID = "track_id";

  private static final String TAG = MarkerListActivity.class.getSimpleName();
  
  private static final String[] PROJECTION = new String[] { WaypointsColumns._ID,
      WaypointsColumns.TYPE,
      WaypointsColumns.NAME,
      WaypointsColumns.CATEGORY,
      WaypointsColumns.TIME,
      WaypointsColumns.DESCRIPTION };

  // Callback when an item is selected in the contextual action mode
  private ContextualActionModeCallback contextualActionModeCallback =
    new ContextualActionModeCallback() {
    @Override
    public boolean onClick(int itemId, long id) {
      return handleContextItem(itemId, id);
    }
  };

  /*
   * Note that sharedPreferenceChangeListener cannot be an anonymous inner
   * class. Anonymous inner class will get garbage collected.
   */
  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
    new OnSharedPreferenceChangeListener() {
    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
      // Note that key can be null
      if (PreferencesUtils.getRecordingTrackIdKey(MarkerListActivity.this).equals(key)) {
        updateMenu();
      }
    }
  };

  private long trackId = -1;
  private ResourceCursorAdapter resourceCursorAdapter;

  // UI elements
  private MenuItem insertMarkerMenuItem;
  private MenuItem searchMenuItem;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    trackId = getIntent().getLongExtra(EXTRA_TRACK_ID, -1L);
    if (trackId == -1L) {
      Log.d(TAG, "invalid track id");
      finish();
      return;
    }
    
    setVolumeControlStream(TextToSpeech.Engine.DEFAULT_STREAM);
    setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
    ApiAdapterFactory.getApiAdapter().configureActionBarHomeAsUp(this);
    setContentView(R.layout.marker_list);

    SharedPreferences sharedPreferences = getSharedPreferences(
        Constants.SETTINGS_NAME, Context.MODE_PRIVATE);
    sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

    ListView listView = (ListView) findViewById(R.id.marker_list);
    listView.setEmptyView(findViewById(R.id.marker_list_empty));
    listView.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        startActivity(new Intent(MarkerListActivity.this, MarkerDetailActivity.class).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MarkerDetailActivity.EXTRA_MARKER_ID, id));
      }
    });
    resourceCursorAdapter = new ResourceCursorAdapter(this, R.layout.marker_list_item, null, 0) {
      @Override
      public void bindView(View view, Context context, Cursor cursor) {
        int typeIndex = cursor.getColumnIndex(WaypointsColumns.TYPE);
        int nameIndex = cursor.getColumnIndex(WaypointsColumns.NAME);
        int categoryIndex = cursor.getColumnIndex(WaypointsColumns.CATEGORY);
        int timeIndex = cursor.getColumnIndexOrThrow(WaypointsColumns.TIME);
        int descriptionIndex = cursor.getColumnIndex(WaypointsColumns.DESCRIPTION);

        boolean statistics = cursor.getInt(typeIndex) == Waypoint.TYPE_STATISTICS;
        TextView name = (TextView) view.findViewById(R.id.marker_list_item_name);
        name.setText(cursor.getString(nameIndex));
        name.setCompoundDrawablesWithIntrinsicBounds(statistics ? R.drawable.ylw_pushpin
            : R.drawable.blue_pushpin, 0, 0, 0);

        TextView category = (TextView) view.findViewById(R.id.marker_list_item_category);
        if (!statistics) {
          category.setText(cursor.getString(categoryIndex));
        }
        category.setVisibility(statistics || category.getText().length() == 0 ? View.GONE : View.VISIBLE);

        TextView time = (TextView) view.findViewById(R.id.marker_list_item_time);
        long timeValue = cursor.getLong(timeIndex);
        if (timeValue == 0) {
          time.setVisibility(View.GONE);
        } else {
          time.setText(StringUtils.formatDateTime(MarkerListActivity.this, timeValue));
          time.setVisibility(View.VISIBLE);
        }

        TextView description = (TextView) view.findViewById(R.id.marker_list_item_description);
        if (!statistics) {
          description.setText(cursor.getString(descriptionIndex));
        }
        description.setVisibility(statistics || description.getText().length() == 0 ? View.GONE : View.VISIBLE);
      }
    };
    listView.setAdapter(resourceCursorAdapter);
    ApiAdapterFactory.getApiAdapter().configureContextualMenu(
        this, listView, R.menu.marker_list_context_menu, R.id.marker_list_item_name,
        contextualActionModeCallback);


    final long firstWaypointId = MyTracksProviderUtils.Factory.get(this).getFirstWaypointId(trackId);
    getSupportLoaderManager().initLoader(0, null, new LoaderCallbacks<Cursor>() {
      @Override
      public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        return new CursorLoader(MarkerListActivity.this,
            WaypointsColumns.CONTENT_URI,
            PROJECTION,
            WaypointsColumns.TRACKID + "=? AND " + WaypointsColumns._ID + "!=?",
            new String[] { String.valueOf(trackId), String.valueOf(firstWaypointId) },
            null);
      }

      @Override
      public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        resourceCursorAdapter.swapCursor(cursor);
      }

      @Override
      public void onLoaderReset(Loader<Cursor> loader) {
        resourceCursorAdapter.swapCursor(null);
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.marker_list, menu);
    insertMarkerMenuItem = menu.findItem(R.id.marker_list_insert_marker);
    searchMenuItem = menu.findItem(R.id.marker_list_search);
    ApiAdapterFactory.getApiAdapter().configureSearchWidget(this, searchMenuItem);
    updateMenu();
    return true;
  }

  private void updateMenu() {
    if (insertMarkerMenuItem != null) {
      insertMarkerMenuItem.setVisible(trackId == PreferencesUtils.getRecordingTrackId(this));
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      case R.id.marker_list_insert_marker:
        startActivity(new Intent(this, MarkerEditActivity.class).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
      case R.id.marker_list_search:
        return ApiAdapterFactory.getApiAdapter().handleSearchMenuSelection(this);
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    getMenuInflater().inflate(R.menu.marker_list_context_menu, menu);
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    if (handleContextItem(
        item.getItemId(), ((AdapterContextMenuInfo) item.getMenuInfo()).id)) {
      return true;
    }
    return super.onContextItemSelected(item);
  }

  /**
   * Handles a context item selection.
   *
   * @param itemId the menu item id
   * @param markerId the marker id
   * @return true if handled.
   */
  private boolean handleContextItem(int itemId, long markerId) {
    switch (itemId) {
      case R.id.marker_list_context_menu_show_on_map:
        startActivity(new Intent(this, TrackDetailActivity.class).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(TrackDetailActivity.EXTRA_MARKER_ID, markerId));
        return true;
      case R.id.marker_list_context_menu_edit:
        startActivity(new Intent(this, MarkerEditActivity.class).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MarkerEditActivity.EXTRA_MARKER_ID, markerId));
        return true;
      case R.id.marker_list_context_menu_delete:
        DeleteOneMarkerDialogFragment.newInstance(markerId).show(getSupportFragmentManager(),
            DeleteOneMarkerDialogFragment.DELETE_ONE_MARKER_DIALOG_TAG);
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_SEARCH) {
      if (ApiAdapterFactory.getApiAdapter().handleSearchKey(searchMenuItem)) { return true; }
    }
    return super.onKeyUp(keyCode, event);
  }
}
