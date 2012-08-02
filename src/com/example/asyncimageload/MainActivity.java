package com.example.asyncimageload;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import ashy.earl.util.AsyncImageLoader;
import ashy.earl.util.AsyncImageLoader.ImageLoadListener;


public class MainActivity extends Activity {
	private AsyncImageLoader mImageLoader;
	public static String URL = "URL";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mImageLoader = AsyncImageLoader.getInstance(this);
		setContentView(R.layout.activity_main);
		ListView listView = (ListView) findViewById(R.id.listView1);
		listView.setAdapter(new MyAdapter(getIntent().getStringArrayExtra(URL)));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mImageLoader = null;
		AsyncImageLoader.release();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	private class MyAdapter extends BaseAdapter {
		private LayoutInflater mInflater;
		private MyImageLoad mImageLoad;
		private String[] mUrls;
		private Map<View, String> mCheck;

		MyAdapter(String[] urls) {
			mInflater = getLayoutInflater();
			mImageLoad = new MyImageLoad();
			mUrls = urls;
			mCheck = new HashMap<View, String>();
		}

		@Override
		public int getCount() {
			return mUrls.length;
		}

		@Override
		public Object getItem(int position) {
			return mUrls[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final ViewHolder holder;
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.list_item, null);
				holder = new ViewHolder();
				holder.image = (ImageView) convertView
						.findViewById(R.id.imageView1);
				holder.txt = (TextView) convertView
						.findViewById(R.id.textView1);
				holder.progress = (ProgressBar) convertView
						.findViewById(R.id.progressBar1);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			holder.image.setImageBitmap(null);
			String url = mUrls[position];
			// System.out.println("----------getView:" + position + ",url:" +
			// url);
			System.out.println(position + " " + mCheck.put(convertView, url));
			mImageLoader.loadImage(convertView, url, mImageLoad);
			return convertView;
		}

		private class ViewHolder {
			ImageView image;
			TextView txt;
			ProgressBar progress;
		}

		private class MyImageLoad extends ImageLoadListener {
			// private Animation mFadeIn;
			//
			// MyImageLoad() {
			// mFadeIn = AnimationUtils.loadAnimation(MainActivity.this,
			// android.R.anim.fade_in);
			// }

			@Override
			public void onSucceed(boolean isAsyncLoad, View view, String url,
					Bitmap bitmap) {
				String checkUrl = mCheck.get(view);
				if (!checkUrl.equals(url)) {
					Log.e("Error", "old url:" + checkUrl + "\nnew url:" + url);
				}
				// if(!checkUrl.equals(mImageLoader.mOrders.get(view).url)){
				// Log.e("Errorsssss", "old url:" + checkUrl + "\nnew url:" +
				// url);
				// }
				// Log.e("earl", "onSucceed " + isAsyncLoad + " " + view + " "
				// + url + " " + bitmap);
				ViewHolder holder = (ViewHolder) view.getTag();
				holder.image.setImageBitmap(bitmap);
				if (isAsyncLoad) {
					holder.image.startAnimation(AnimationUtils.loadAnimation(
							getApplication(), android.R.anim.fade_in));
				}
				holder.txt.setTextColor(Color.WHITE);
				holder.txt.setText("Load Succeed");

				holder.progress.setVisibility(View.INVISIBLE);
			}

			@Override
			public void onStart(View view, String url) {
				ViewHolder holder = (ViewHolder) view.getTag();
				// if (holder.progress.getVisibility() != View.VISIBLE) {
				holder.txt.setTextColor(Color.WHITE);
				holder.txt.setText("Loading...");
				holder.progress.setVisibility(View.VISIBLE);
				// }
			}

			@Override
			public void onFailed(View view, String url) {
				ViewHolder holder = (ViewHolder) view.getTag();
				// if (!holder.txt.getText().equals("Load Failed!!!")) {
				holder.txt.setTextColor(Color.RED);
				holder.txt.setText("Load Failed!!!");
				holder.progress.setVisibility(View.INVISIBLE);
				// }
			}
		}
	}

}
