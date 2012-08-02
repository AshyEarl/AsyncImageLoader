package com.example.asyncimageload;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import ashy.earl.util.AsyncImageLoader;
import ashy.earl.util.AsyncImageLoader.ImageLoadListener;

public class ShowPicActivity extends Activity {
	public static final String URL = "url";
	public static final String SINGLE_COUNT = "SINGLE_COUNT";
	private int mStart;
	private int mSingleCount;
	private String mUrl;
	private MyAdapter mAdapter;
	private Button mLoadMore;
	private View mLoadMoreProgerss;
	private String mResultCount;
	private AsyncImageLoader mAsyncImageLoader;
	private View mProgress;
	private ListView mListView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.show_picture);
		Intent intent = getIntent();
		mUrl = intent.getStringExtra(URL);
		mSingleCount = intent.getIntExtra(SINGLE_COUNT, 4);
		mAsyncImageLoader = AsyncImageLoader.getInstance(this);

		mAdapter = new MyAdapter();
		mListView = (ListView) findViewById(R.id.listView1);
		View fonter = getLayoutInflater().inflate(R.layout.load_button, null);
		mLoadMore = (Button) fonter.findViewById(R.id.load_more);
		mLoadMoreProgerss = fonter.findViewById(R.id.load_more_progress);
		mLoadMore.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				System.out.println("mLoadMore onClick");
				mLoadMore.setText("");
				mLoadMore.setEnabled(false);
				mLoadMoreProgerss.setVisibility(View.VISIBLE);
				mLoadMore.invalidate();
				loadMore(false);
			}
		});
		mListView.addFooterView(fonter);
		mListView.setAdapter(mAdapter);
		mListView.setVisibility(View.INVISIBLE);
		mProgress = findViewById(R.id.progress);
		mProgress.setVisibility(View.VISIBLE);
		loadMore(true);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		AsyncImageLoader.release();
	}

	private void loadMore(final boolean isFristRun) {
		final String url = mUrl + "&start=" + mStart;
		Log.e("loadMore url:", url);
		new AsyncTask<Object, Object, List<Picture>>() {

			@Override
			protected List<Picture> doInBackground(Object... params) {
				URLConnection urlConnection;
				try {
					urlConnection = new URL(url).openConnection();
					urlConnection.setConnectTimeout(5 * 1000);
					urlConnection.setReadTimeout(5 * 1000);
					String result = inputStream2String(urlConnection
							.getInputStream());
					List<Picture> pics = parseJson(result);
					return pics;
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				return null;
			}

			protected void onPostExecute(List<Picture> result) {
				mAdapter.addPictures(result);
				Toast.makeText(ShowPicActivity.this,
						"搜索到结果:" + mResultCount + "条", Toast.LENGTH_LONG)
						.show();
				if (isFristRun) {
					mProgress.setVisibility(View.INVISIBLE);
					mListView.setVisibility(View.VISIBLE);
				} else {
					mLoadMore.setText("加载更多...");
					mLoadMore.setEnabled(true);
					mLoadMoreProgerss.setVisibility(View.INVISIBLE);
				}
			};
		}.execute(null, null);
		mStart += mSingleCount;
	}

	private List<Picture> parseJson(String result) {
		System.out.println(result);
		try {
			JSONObject jsonObject = new JSONObject(result);
			JSONObject responseData = jsonObject.getJSONObject("responseData");
			mResultCount = responseData.getJSONObject("cursor").getString(
					"estimatedResultCount");
			JSONArray results = responseData.getJSONArray("results");
			int resultCount = results.length();
			List<Picture> pictures = new ArrayList<ShowPicActivity.Picture>(
					resultCount);
			for (int i = 0; i < resultCount; i++) {
				JSONObject single = results.getJSONObject(i);
				Picture picture = new Picture();
				picture.height = single.getInt("height");
				picture.width = single.getInt("width");
				picture.title = single.getString("title");
				picture.url = single.getString("unescapedUrl");
				picture.width_height = picture.width + " * " + picture.height;
				pictures.add(picture);
			}
			return pictures;

		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}

	}

	public String inputStream2String(InputStream in) throws IOException {
		StringBuffer out = new StringBuffer();
		byte[] b = new byte[4096];
		for (int n; (n = in.read(b)) != -1;) {
			out.append(new String(b, 0, n));
		}
		in.close();
		return out.toString();
	}

	private class MyAdapter extends BaseAdapter {
		private List<Picture> mPictures;
		private LayoutInflater mInflater;
		private MyImageLinstener mLinstener;

		MyAdapter() {
			mPictures = new LinkedList<ShowPicActivity.Picture>();
			mInflater = getLayoutInflater();
			mLinstener = new MyImageLinstener();
		}

		private void addPictures(List<Picture> pictures) {
			if (pictures == null || pictures.size() == 0) {
				return;
			}
			mPictures.addAll(pictures);
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mPictures.size();
		}

		@Override
		public Object getItem(int arg0) {
			return mPictures.get(arg0);
		}

		@Override
		public long getItemId(int arg0) {
			return arg0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder = null;
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.google_search_item,
						null);
				holder = new ViewHolder();
				holder.pic = (ImageView) convertView
						.findViewById(R.id.imageView1);
				holder.progress = (ProgressBar) convertView
						.findViewById(R.id.progressBar1);
				holder.size = (TextView) convertView.findViewById(R.id.size);
				holder.title = (TextView) convertView.findViewById(R.id.title);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			Picture picture = mPictures.get(position);
			holder.size.setText(picture.width_height);
			holder.title.setText(picture.title);
			mAsyncImageLoader.loadImage(convertView, picture.url, mLinstener);
			// do something
			return convertView;
		}

		private class ViewHolder {
			ImageView pic;
			TextView title;
			TextView size;
			ProgressBar progress;
		}

		private class MyImageLinstener extends ImageLoadListener {

			@Override
			public void onSucceed(boolean isAsyncLoad, View view, String url,
					Bitmap bitmap) {
				ViewHolder holder = (ViewHolder) view.getTag();
				holder.progress.setVisibility(View.INVISIBLE);
				holder.pic.setImageBitmap(bitmap);
				holder.title.setTextColor(Color.WHITE);
			}

			@Override
			public void onStart(View view, String url) {
				ViewHolder holder = (ViewHolder) view.getTag();
				holder.progress.setVisibility(View.VISIBLE);
				holder.pic.setImageBitmap(null);
				holder.title.setTextColor(Color.WHITE);
			}

			@Override
			public void onFailed(View view, String url) {
				ViewHolder holder = (ViewHolder) view.getTag();
				holder.progress.setVisibility(View.INVISIBLE);
				holder.pic.setImageBitmap(null);
				holder.title.setTextColor(Color.RED);
			}

		}
	}

	private class Picture {
		String url;
		String title;
		int width;
		int height;
		String width_height;
	}
}
