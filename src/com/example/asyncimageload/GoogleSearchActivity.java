package com.example.asyncimageload;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.ToggleButton;

public class GoogleSearchActivity extends Activity {
	private static final String BASE_URL = "http://ajax.googleapis.com/ajax/services/search/images?v=1.0";

	private EditText q;
	private EditText as_sitesearch;
	private EditText rsz;
	private ToggleButton imgc;
	private ToggleButton safe;
	private RadioGroup as_filetype;
	private RadioGroup imgsz;
	private Button go;

	private String url;
	private int mSingleCount;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.google_search);

		q = (EditText) findViewById(R.id.q);
		as_sitesearch = (EditText) findViewById(R.id.as_sitesearch);
		rsz = (EditText) findViewById(R.id.rsz);
		imgc = (ToggleButton) findViewById(R.id.imgc);
		safe = (ToggleButton) findViewById(R.id.safe);
		as_filetype = (RadioGroup) findViewById(R.id.as_filetype);
		imgsz = (RadioGroup) findViewById(R.id.imgsz);
		go = (Button) findViewById(R.id.go);

		go.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (checkParams()) {
					Intent intent = new Intent(GoogleSearchActivity.this,
							ShowPicActivity.class);
					intent.putExtra(ShowPicActivity.URL, url);
					intent.putExtra(ShowPicActivity.SINGLE_COUNT, mSingleCount);
					startActivity(intent);
				}
			}
		});
	}

	@SuppressWarnings("deprecation")
	private boolean checkParams() {
		String q = this.q.getEditableText().toString();
		String as_sitesearch = this.as_sitesearch.getEditableText().toString();
		String rsz = this.rsz.getEditableText().toString();
		boolean imgc = this.imgc.isChecked();
		boolean safe = this.safe.isChecked();
		int as_filetype = this.as_filetype.getCheckedRadioButtonId();
		int imgsz = this.imgsz.getCheckedRadioButtonId();
		if (TextUtils.isEmpty(q)) {
			toast("亲,搜索的关键字不能为空哟");
			return false;
		}
		try {
			mSingleCount = Integer.valueOf(rsz);
		} catch (NumberFormatException e) {
			toast("单次加载的图片个数必须是数字哟");
			return false;
		}
		if (mSingleCount < 1 || mSingleCount > 8) {
			toast("单次加载的图片个数必须是大于1小于8的数字");
			return false;
		}
		if (!TextUtils.isEmpty(as_sitesearch)
				&& !as_sitesearch
						.matches("([\\w-]+\\.)+[\\w-]+(/[\\w- ./?%&=]*)?")) {
			toast(as_sitesearch + " 就不是个网址么");
			return false;
		}
		StringBuffer sb = new StringBuffer();
		sb.append(BASE_URL);
		sb.append("&q=");
		sb.append(java.net.URLEncoder.encode(q));
		if (!TextUtils.isEmpty(as_sitesearch)) {
			sb.append("&as_sitesearch=");
			sb.append(as_sitesearch);
		}
		sb.append("&rsz=");
		sb.append(rsz);
		sb.append("&imgc=");
		sb.append(imgc ? "color" : "gray");
		sb.append("&safe=");
		sb.append(safe ? "active" : "off");
		switch (as_filetype) {
		case R.id.jpg:
			sb.append("&as_filetype=");
			sb.append("jpg");
			break;
		case R.id.png:
			sb.append("&as_filetype=");
			sb.append("png");
			break;
		case R.id.bmp:
			sb.append("&as_filetype=");
			sb.append("bmp");
			break;
		case R.id.none:
			break;
		default:
			break;
		}
		switch (imgsz) {
		case R.id.icon:
			sb.append("&imgsz=");
			sb.append("icon");
			break;
		case R.id.small:
			sb.append("&imgsz=");
			sb.append("small");
			break;
		case R.id.medium:
			sb.append("&imgsz=");
			sb.append("medium");
			break;
		case R.id.large:
			sb.append("&imgsz=");
			sb.append("large");
			break;
		case R.id.xlarge:
			sb.append("&imgsz=");
			sb.append("xlarge");
			break;
		case R.id.xxlarge:
			sb.append("&imgsz=");
			sb.append("xxlarge");
			break;
		case R.id.huge:
			sb.append("&imgsz=");
			sb.append("huge");
			break;
		case R.id.noneSize:
			break;
		default:
			break;
		}
		System.out.println(sb);
		url = sb.toString();
		return true;
	}

	private void toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
	}
}
