package com.applidium.shutterbugdemo;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.applidium.shutterbug.FetchableImageView;
import com.applidium.shutterbug.cache.ImageCache;

public class ShutterbugActivity extends Activity {
    private ListView       mListView;
    private DemoAdapter    mAdapter;
    private ProgressDialog mProgressDialog;
    private List<String>   mUrls   = new ArrayList<String>();
    private List<String>   mTitles = new ArrayList<String>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shutterbug);

        mListView = (ListView) findViewById(R.id.list);
        mAdapter = new DemoAdapter();
        mListView.setAdapter(mAdapter);

        Button b = (Button) findViewById(R.id.clear_cache_button);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                ImageCache.getSharedImageCache(ShutterbugActivity.this).clear();
                mAdapter.notifyDataSetChanged();
            }
        });

        loadGalleryContents();
    }

    private class DemoAdapter extends BaseAdapter {

        public int getCount() {
            return mUrls.size();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.shutterbug_demo_row, null);
            }
            
            TextView text = (TextView) view.findViewById(R.id.text);
            text.setText("#" + position + ": " + mTitles.get(position));

            FetchableImageView image = (FetchableImageView) view.findViewById(R.id.image);
            image.setImage(mUrls.get(position));

            return view;
        }
    }

    private void loadGalleryContents() {
        mProgressDialog = ProgressDialog.show(this, "", getString(R.string.loading));
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    URL url = new URL("http://imgur.com/gallery/top/all.json");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestProperty("User-Agent", "");
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    JSONObject result = new JSONObject(new java.util.Scanner(in).useDelimiter("\\A").next());
                    if (result.has("data")) {
                        JSONArray data = result.getJSONArray("data");
                        mUrls.clear();
                        mTitles.clear();
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject dataObject = data.getJSONObject(i);
                            mUrls.add("http://api.imgur.com/" + dataObject.getString("hash") + "s" + dataObject.getString("ext"));
                            mTitles.add(dataObject.getString("title"));
                        }
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                super.onPostExecute(result);
                mAdapter.notifyDataSetChanged();
                mProgressDialog.dismiss();
            }

        }.execute();

    }
}
