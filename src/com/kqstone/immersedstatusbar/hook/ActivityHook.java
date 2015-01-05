package com.kqstone.immersedstatusbar.hook;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;

import com.kqstone.immersedstatusbar.Const;
import com.kqstone.immersedstatusbar.Utils;
import com.kqstone.immersedstatusbar.Utils.WindowType;
import com.kqstone.immersedstatusbar.helper.BitMapColor;
import com.kqstone.immersedstatusbar.helper.BitMapColor.Type;
import com.kqstone.immersedstatusbar.helper.ProfileHelper;
import com.kqstone.immersedstatusbar.helper.ReflectionHelper;

public class ActivityHook {
	public static final Class<?> sClazz = Activity.class;
	private static final String[] FastTransApp = { "com.miui.home",
			"com.UCMobile", "com.tencent.mm", "com.sina.weibo" };

	private static final String[][] IgnorApp = { { "com.baidu.netdisk",
			"ui.MainActivity" } };

	public Activity mActivity;
	private String mPkgName = null, mActName = null;
	private ProfileHelper mHelper = null;
	private boolean mHasProfile = false;

	private Integer mBackgroundType = null;
	private int mColor = 0;
	private String mPath = null;
	private boolean mDarkMode = false;
	private boolean mFastTrans = false;

	private boolean mNeedGetColorFromBackground = false;
	private boolean mRepaddingHandled = false;
	private boolean mHasSetWindowBackground = false;
	private boolean mCreateAct = false;

	public ActivityHook(Object activity) {
		mActivity = (Activity) activity;
	}

	public void hookAfterOnCreate() {
		mPkgName = mActivity.getPackageName();
		mActName = mActivity.getLocalClassName();
		mHelper = new ProfileHelper(mActivity.getPackageName());
		mHelper.initiateProfile(mActivity.getLocalClassName());
		if (mHelper != null && mHelper.hasProfile()) {
			mHasProfile = true;
			mBackgroundType = mHelper.getBackgroundType();
			if (mBackgroundType == 2)
				Utils.setTranslucentStatus(mActivity);
		}
		mCreateAct = true;
	}

	public void hookAfterPerformResume() {
		Utils.logoutActivityInform(mActivity);

		// Ignor app
		for (String[] ignorApp : IgnorApp) {
			if (ignorApp[0].equals(mPkgName) && ignorApp[1].equals(mActName))
				return;
		}

		WindowType type = Utils.getWindowType(mActivity);
		Utils.log("Resume: Window type: " + type);
		if (type == WindowType.Float)
			return;

		// For Activity that has been resumed before
		Utils.log("mCreateAct: " + mCreateAct);
		if (!mCreateAct) {
			Utils.sendTintStatusBarIntent(mActivity, mBackgroundType, mColor,
					mPath, mDarkMode, mFastTrans);
			return;
		}

		// mFastTrans
		for (String s : FastTransApp) {
			if (s.equals(mPkgName)) {
				mFastTrans = true;
				break;
			}
		}
		if (!mFastTrans) {
			if (mHasProfile) {
				mFastTrans = mHelper.getFastTrans();
			}
		}

		boolean handled = false;
		switch (type) {
		case Fullscreen:
			mColor = Color.parseColor("#33000000");
			handled = true;
			mDarkMode = false;
			Utils.sendTintStatusBarIntent(mActivity, mBackgroundType, mColor,
					mPath, mDarkMode, mFastTrans);
			break;
		case Translucent:
			Utils.log("Translucent activity, need get darkmode after window focus changed");
			mNeedGetColorFromBackground = true;
			break;
		case Normal:
			boolean exinform = Settings.System.getInt(
					mActivity.getContentResolver(),
					Const.KEY_PREF_EXPORT_INFORM, 0) == 1 ? true : false;
			if (exinform)
				Utils.logStandXml(mActivity);
			boolean exinformtofile = Settings.System.getInt(
					mActivity.getContentResolver(),
					Const.KEY_PREF_EXPORT_INFORM_TOFILE, 0) == 1 ? true : false;
			if (exinformtofile)
				Utils.exportStandXml(mActivity);

			Drawable drawable = null;
			if (mHasProfile) {
				mBackgroundType = mHelper.getBackgroundType();
				int k = mHelper.getPaddingOffset();
				if (k != 0) {
					Utils.resetPadding(mActivity, k);
				}
				switch (mBackgroundType) {
				case 0:
					mColor = mHelper.getColor();
					mDarkMode = Utils.getDarkMode(mColor);
					handled = true;
					drawable = new ColorDrawable(mColor);
					break;
				case 1:
					mColor = Const.UNKNOW_COLOR;
					mPath = mHelper.getBackgroundPath();
					Bitmap tempmap = mHelper.getBitmap();
					mDarkMode = Utils
							.getDarkMode(Utils.getBitmapColor(tempmap).Color);
					handled = true;
					drawable = new BitmapDrawable(tempmap);
					break;
				}
			}

			if (!handled) {
				ActionBar actionBar = mActivity.getActionBar();
				if (actionBar != null) {
					FrameLayout container = (FrameLayout) ReflectionHelper
							.getObjectField(actionBar, "mContainerView");
					if (container != null) {
						Drawable backgroundDrawable = (Drawable) ReflectionHelper
								.getObjectField(container, "mBackground");
						if (backgroundDrawable != null) {
							mColor = Utils
									.getMainColorFromActionBarDrawable(backgroundDrawable);
							mDarkMode = Utils.getDarkMode(mColor);
							handled = true;
							drawable = new ColorDrawable(mColor);
							actionBar.setBackgroundDrawable(drawable);
							container.invalidate();
							mBackgroundType = 0;
						}
					}
				}
			}

			if (drawable != null && !mHasSetWindowBackground) {
				Utils.setDecorViewBackground(mActivity, drawable, false);
				mHasSetWindowBackground = true;
			}

			if (!handled) {
				mNeedGetColorFromBackground = true;
				Utils.log("can't handle color, need to get color from drawcache after widow focus changed");
			} else {
				Utils.log("get it, send tintstatusbar intent>>>>>>>>>>>>>>>");
				Utils.sendTintStatusBarIntent(mActivity, mBackgroundType,
						mColor, mPath, mDarkMode, mFastTrans);
			}
			break;
		case Float:
			break;
		default:
			break;
		}

		mCreateAct = false;
		Utils.log("set mCreateAct to false");
	}

	public void hookAfterOnWindowFocusChanged(boolean focused) {
		if (!focused)
			return;

		if (!mNeedGetColorFromBackground)
			return;

		Bitmap bitmap;
		WindowType type = Utils.getWindowType(mActivity);
		switch (type) {
		case Normal:
			boolean exinformtofile = Settings.System.getInt(
					mActivity.getContentResolver(),
					Const.KEY_PREF_EXPORT_INFORM_TOFILE, 0) == 1 ? true : false;
			if (exinformtofile) {
				View view = mActivity.getWindow().getDecorView();
				view.destroyDrawingCache();
				view.setDrawingCacheEnabled(true);
				bitmap = view.getDrawingCache();
				if (bitmap != null)
					Utils.outputBitmapToFile(bitmap, mActivity);
			}

			mBackgroundType = 0;
			mPath = null;

			Drawable drawable = null;
			bitmap = Utils.getBitMapFromActivityBackground(mActivity, false);
			if (bitmap != null) {
				BitMapColor bitmapColor = Utils.getBitmapColor(bitmap);
				if (bitmapColor.mType == Type.FLAT) {
					Utils.log("Flat BitMap found...");
					mColor = bitmapColor.Color;
					mDarkMode = Utils.getDarkMode(mColor);
					drawable = new ColorDrawable(mColor);
				} else if (bitmapColor.mType == Type.GRADUAL) {
					Utils.log("GRADUAL BitMap found, rePadding viewgroup...");
					mColor = bitmapColor.Color;
					mDarkMode = Utils.getDarkMode(mColor);
					if (!mRepaddingHandled) {
						Utils.resetPadding(mActivity,
								Const.OFFEST_FOR_GRADUAL_ACTIVITY);
						mRepaddingHandled = true;
					}
					drawable = new ColorDrawable(mColor);
				} else if (bitmapColor.mType == Type.PICTURE) {
					Utils.log("Flat BitMap found...");
					if (Settings.System.getInt(mActivity.getContentResolver(),
							Const.KEY_PREF_FORCE_TINT, 0) == 1) {
						mColor = bitmapColor.Color;
						mDarkMode = Utils.getDarkMode(mColor);
						drawable = new ColorDrawable(mColor);
					}
				}
			}

			if (drawable != null && !mHasSetWindowBackground) {
				Utils.setDecorViewBackground(mActivity, drawable, true);
				mHasSetWindowBackground = true;
			}

			Utils.sendTintStatusBarIntent(mActivity, mBackgroundType, mColor,
					mPath, mDarkMode, mFastTrans);
			break;

		case Translucent:
			mBackgroundType = 0;
			mColor = Color.TRANSPARENT;
			mPath = null;

			bitmap = Utils.getBitMapFromActivityBackground(mActivity, true);
			if (bitmap != null) {
				BitMapColor bitmapColor = Utils.getBitmapColor(bitmap);
				int color = bitmapColor.Color;
				mDarkMode = Utils.getDarkMode(color);
			}
			Utils.sendTintStatusBarIntent(mActivity, mBackgroundType, mColor,
					mPath, mDarkMode, mFastTrans);
			break;

		default:
			break;
		}

		mNeedGetColorFromBackground = false;
	}
}