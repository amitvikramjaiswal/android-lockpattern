/*
 *   Copyright 2012 Hai Bison
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package haibison.android.lockpattern;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import haibison.android.lockpattern.utils.AlpSettings;
import haibison.android.lockpattern.utils.Encrypter;
import haibison.android.lockpattern.utils.InvalidEncrypterException;
import haibison.android.lockpattern.utils.LoadingView;
import haibison.android.lockpattern.utils.ResourceUtils;
import haibison.android.lockpattern.utils.UI;
import haibison.android.lockpattern.widget.LockPatternUtils;
import haibison.android.lockpattern.widget.LockPatternView;
import haibison.android.lockpattern.widget.LockPatternView.Cell;
import haibison.android.lockpattern.widget.LockPatternView.DisplayMode;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static haibison.android.lockpattern.BuildConfig.DEBUG;
import static haibison.android.lockpattern.utils.AlpSettings.Display.METADATA_CAPTCHA_WIRED_DOTS;
import static haibison.android.lockpattern.utils.AlpSettings.Display.METADATA_MAX_RETRIES;
import static haibison.android.lockpattern.utils.AlpSettings.Display.METADATA_MIN_WIRED_DOTS;
import static haibison.android.lockpattern.utils.AlpSettings.Display.METADATA_STEALTH_MODE;
import static haibison.android.lockpattern.utils.AlpSettings.Security.METADATA_AUTO_SAVE_PATTERN;
import static haibison.android.lockpattern.utils.AlpSettings.Security.METADATA_ENCRYPTER_CLASS;

/**
 * Main activity for this library.
 * <p>
 * You can deliver result to {@link android.app.PendingIntent PendingIntent}'s and/ or {@link android.os.ResultReceiver ResultReceiver} too.
 * See {@link #EXTRA_PENDING_INTENT_OK}, {@link #EXTRA_PENDING_INTENT_CANCELLED} and {@link #EXTRA_RESULT_RECEIVER} for more details.
 * <p>
 * <h1>NOTES</h1>
 * <p>
 * <ul>
 * <li> You must use one of built-in actions when calling this activity. They start with {@code ACTION_*}. Otherwise the library might behave
 * strangely (we don't cover those cases).</li>
 * <li>You must use one of the themes that this library supports. They start with {@code R.style.Alp_42447968_Theme_*}. The reason is the themes
 * contain resources that the library needs.</li>
 * <li>With {@link #ACTION_COMPARE_PATTERN}, there are <b><i>4 possible result codes</i></b>: {@link android.app.Activity#RESULT_OK RESULT_OK},
 * {@link android.app.Activity#RESULT_CANCELED RESULT_CANCELED}, {@link #RESULT_FAILED} and {@link #RESULT_FORGOT_PATTERN}.</li>
 * <li>With {@link #ACTION_VERIFY_CAPTCHA}, there are <b><i>3 possible result codes</i></b>: {@link android.app.Activity#RESULT_OK RESULT_OK},
 * {@link android.app.Activity#RESULT_CANCELED RESULT_CANCELED}, and {@link #RESULT_FAILED}.</li>
 * </ul>
 *
 * @author Hai Bison
 * @since v1.0
 */
public class LockPatternActivity extends Activity {

    private static final String CLASSNAME = LockPatternActivity.class.getName();

    /**
     * Use this action to create new pattern. You can provide an {@link haibison.android.lockpattern.utils.Encrypter Encrypter} with {@link
     * haibison.android.lockpattern.utils.AlpSettings.Security#setEncrypterClass(android.content.Context, Class) Security.setEncrypterClass()}
     * to improve security.
     * <p>
     * If the user created a pattern, {@link android.app.Activity#RESULT_OK RESULT_OK} returns with the pattern ({@link #EXTRA_PATTERN}).
     * Otherwise {@link android.app.Activity#RESULT_CANCELED RESULT_CANCELED} returns.
     *
     * @see #EXTRA_PENDING_INTENT_OK
     * @see #EXTRA_PENDING_INTENT_CANCELLED
     * @since v2.4 beta
     */
    public static final String ACTION_CREATE_PATTERN = CLASSNAME + ".CREATE_PATTERN";

    /**
     * Use this action to compare pattern. You provide the pattern to be compared with {@link #EXTRA_PATTERN}.
     * <p>
     * If you enabled feature auto-save pattern before (with
     * {@link haibison.android.lockpattern.utils.AlpSettings.Security#setAutoSavePattern(Context, boolean) Security.setAutoSavePattern()}), then
     * you don't need {@link #EXTRA_PATTERN} at this time. But if you use this extra, its priority is higher than the one stored in shared
     * preferences.
     * <p>
     * You can use {@link #EXTRA_PENDING_INTENT_FORGOT_PATTERN} to help your users in case they forgot the patterns.
     * <p>
     * If the user passes, {@link android.app.Activity#RESULT_OK RESULT_OK} returns. If not, {@link #RESULT_FAILED} returns.
     * <p>
     * If the user cancels the task, {@link android.app.Activity#RESULT_CANCELED RESULT_CANCELED} returns.
     * <p>
     * In any case, extra {@link #EXTRA_RETRY_COUNT} will always be available in the intent result.
     *
     * @see #EXTRA_PATTERN
     * @see #EXTRA_PENDING_INTENT_OK
     * @see #EXTRA_PENDING_INTENT_CANCELLED
     * @see #RESULT_FAILED
     * @see #EXTRA_RETRY_COUNT
     * @since v2.4 beta
     */
    public static final String ACTION_COMPARE_PATTERN = CLASSNAME + ".COMPARE_PATTERN";

    /**
     * Use this action to let the activity generate a random pattern and ask the user to re-draw it to verify.
     * <p>
     * The default length of the auto-generated pattern is {@code 4}. You can change it with {@link
     * haibison.android.lockpattern.utils.AlpSettings.Display#setCaptchaWiredDots(Context, int) Display.setCaptchaWiredDots()}.
     *
     * @since v2.7 beta
     */
    public static final String ACTION_VERIFY_CAPTCHA = CLASSNAME + ".VERIFY_CAPTCHA";

    /**
     * If you use {@link #ACTION_COMPARE_PATTERN} and the user fails to "login" after a number of tries, this activity will finish with this result code.
     *
     * @see #ACTION_COMPARE_PATTERN
     * @see #EXTRA_RETRY_COUNT
     */
    public static final int RESULT_FAILED = RESULT_FIRST_USER + 1;

    /**
     * If you use {@link #ACTION_COMPARE_PATTERN} and the user forgot his/ her pattern and decided to ask for your help with recovering the
     * pattern ({@link #EXTRA_PENDING_INTENT_FORGOT_PATTERN}), this activity will finish with this result code.
     *
     * @see #ACTION_COMPARE_PATTERN
     * @see #EXTRA_RETRY_COUNT
     * @see #EXTRA_PENDING_INTENT_FORGOT_PATTERN
     * @since v2.8 beta
     */
    public static final int RESULT_FORGOT_PATTERN = RESULT_FIRST_USER + 2;

    /**
     * For actions {@link #ACTION_COMPARE_PATTERN} and {@link #ACTION_VERIFY_CAPTCHA}, this key holds the number of tries that the user
     * attempted to verify the input pattern.
     */
    public static final String EXTRA_RETRY_COUNT = CLASSNAME + ".RETRY_COUNT";

    /**
     * Sets value of this key to a theme in {@code R.style.Alp_42447968_Theme_*} . Default is the one you set in your {@code AndroidManifest.xml}.
     * Note that theme {@link R.style#Alp_42447968_Theme_Light_DarkActionBar} is available in API 4+, but it only works in API 14+.
     *
     * @since v1.5.3 beta
     */
    public static final String EXTRA_THEME = CLASSNAME + ".THEME";

    /**
     * Key to hold the pattern. It must be a {@code char[]} array.
     * <p>
     * <ul>
     * <li>If you use encrypter, it should be an encrypted array.</li>
     * <li>If you don't use encrypter, it should be the SHA-1 value of the actual pattern. You can generate the value by
     * {@link haibison.android.lockpattern.widget.LockPatternUtils#patternToSha1(List) LockPatternUtils.patternToSha1()}.</li>
     * </ul>
     *
     * @since v2 beta
     */
    public static final String EXTRA_PATTERN = CLASSNAME + ".PATTERN";

    /**
     * You can provide an {@link android.os.ResultReceiver ResultReceiver} with this key. The activity will notify your receiver the same result
     * code and intent data as you will receive them in {@link #onActivityResult(int, int, Intent)}.
     *
     * @since v2.4 beta
     */
    public static final String EXTRA_RESULT_RECEIVER = CLASSNAME + ".RESULT_RECEIVER";

    /**
     * Put a {@link android.app.PendingIntent PendingIntent} into this key. It will be sent before {@link android.app.Activity#RESULT_OK RESULT_OK}
     * will be returning. If you were calling this activity with {@link #ACTION_CREATE_PATTERN}, key {@link #EXTRA_PATTERN} will be attached to
     * the original intent which the pending intent holds.
     * <p>
     * <h1>Notes</h1>
     * <p>
     * <ul>
     * <li>If you're going to use an activity, you don't need {@link android.content.Intent#FLAG_ACTIVITY_NEW_TASK FLAG_ACTIVITY_NEW_TASK} for
     * the intent, since the library will call it inside {@link LockPatternActivity} .</li>
     * </ul>
     */
    public static final String EXTRA_PENDING_INTENT_OK = CLASSNAME + ".PENDING_INTENT_OK";

    /**
     * Put a {@link android.app.PendingIntent PendingIntent} into this key. It will be sent before
     * {@link android.app.Activity#RESULT_CANCELED RESULT_CANCELED} will be returning.
     * <p>
     * <h1>Notes</h1>
     * <p>
     * <ul>
     * <li>If you're going to use an activity, you don't need {@link android.content.Intent#FLAG_ACTIVITY_NEW_TASK FLAG_ACTIVITY_NEW_TASK} for
     * the intent, since the library will call it inside {@link LockPatternActivity} .</li>
     * </ul>
     */
    public static final String EXTRA_PENDING_INTENT_CANCELLED = CLASSNAME + ".PENDING_INTENT_CANCELLED";

    /**
     * You put a {@link android.app.PendingIntent PendingIntent} into this extra. The library will show a button <i>"Forgot pattern?"</i> and call
     * your intent later when the user taps it.
     * <p>
     * <h1>Notes</h1>
     * <p>
     * <ul>
     * <li>If you use an activity, you don't need {@link android.content.Intent#FLAG_ACTIVITY_NEW_TASK FLAG_ACTIVITY_NEW_TASK} for the intent,
     * since the library will call it inside {@link LockPatternActivity}.</li>
     * <li>{@link LockPatternActivity} will finish with {@link #RESULT_FORGOT_PATTERN} <i><b>after</b> making a call</i> to start your pending
     * intent.</li>
     * <li>It is your responsibility to make sure the Intent is good. The library doesn't cover any errors when calling your intent.</li>
     * </ul>
     *
     * @author Thanks to Yan Cheng Cheok for his idea.
     * @see #ACTION_COMPARE_PATTERN
     * @since v2.8 beta
     */
    public static final String EXTRA_PENDING_INTENT_FORGOT_PATTERN = CLASSNAME + ".PENDING_INTENT_FORGOT_PATTERN";

    /**
     * Intent builder.
     */
    public static class IntentBuilder {

        /**
         * Makes new builder with {@link #ACTION_CREATE_PATTERN}.
         *
         * @param context the context.
         * @return new builder.
         */
        @NonNull
        public static IntentBuilder newPatternCreator(@NonNull Context context) {
            return new IntentBuilder(context, LockPatternActivity.class, ACTION_CREATE_PATTERN);
        }//newPatternCreator()

        /**
         * Makes new builder with {@link #ACTION_COMPARE_PATTERN}.
         *
         * @param context the context.
         * @param pattern the pattern.
         * @return new builder.
         */
        @NonNull
        public static IntentBuilder newPatternComparator(@NonNull Context context, @Nullable char[] pattern) {
            return new IntentBuilder(context, LockPatternActivity.class, ACTION_COMPARE_PATTERN)
                    .setPattern(pattern);
        }//newPatternComparator()

        /**
         * Makes new builder with {@link #ACTION_COMPARE_PATTERN}.
         *
         * @param context the context.
         * @return new builder.
         */
        @NonNull
        public static IntentBuilder newPatternComparator(@NonNull Context context) {
            return newPatternComparator(context, null);
        }//newPatternComparator()

        /**
         * Makes new builder with {@link #ACTION_VERIFY_CAPTCHA}.
         *
         * @param context the context.
         * @return new builder.
         */
        @NonNull
        public static IntentBuilder newCaptchaVerifier(@NonNull Context context) {
            return new IntentBuilder(context, LockPatternActivity.class, ACTION_VERIFY_CAPTCHA);
        }//newCaptchaVerifier()

        private final Context mContext;
        private final Intent mIntent;

        /**
         * Makes new instance.
         *
         * @param context the context.
         * @param clazz   activity class.
         * @param action  action.
         */
        public IntentBuilder(@NonNull Context context, @NonNull Class<? extends Activity> clazz,
                             @NonNull String action) {
            mContext = context;
            mIntent = new Intent(action, null, context, clazz);
        }//IntentBuilder()

        /**
         * Gets the intent being built.
         *
         * @return the intent.
         */
        @NonNull
        public Intent getIntent() {
            return mIntent;
        }//getIntent()

        /**
         * Builds the final intent.
         *
         * @return the final intent.
         */
        @NonNull
        public Intent build() {
            return mIntent;
        }//build()

        /**
         * Builds the intent via {@link #build()} and calls {@link android.app.Activity#startActivityForResult(Intent, int)
         * Activity.startActivityForResult()}.
         *
         * @param activity    your activity.
         * @param requestCode request code.
         */
        public void startForResult(@NonNull Activity activity, int requestCode) {
            activity.startActivityForResult(build(), requestCode);
        }//startForResult()

        /**
         * Builds the intent via {@link #build()} and calls {@link android.app.Activity#startActivityForResult(Intent, int, Bundle)
         * Activity.startActivityForResult()}.
         *
         * @param activity    your activity.
         * @param requestCode request code.
         * @param options     options.
         */
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        public void startForResult(@NonNull Activity activity, int requestCode, @Nullable Bundle options) {
            activity.startActivityForResult(build(), requestCode, options);
        }//startForResult()

        /**
         * Builds the intent via {@link #build()} and calls {@link android.app.Fragment#startActivityForResult(Intent, int)
         * Fragment.startActivityForResult()}.
         *
         * @param fragment    your fragment.
         * @param requestCode request code.
         */
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        public void startForResult(@NonNull Fragment fragment, int requestCode) {
            fragment.startActivityForResult(build(), requestCode);
        }//startForResult()

        /**
         * Builds the intent via {@link #build()} and calls {@link android.app.Fragment#startActivityForResult(Intent, int, Bundle)
         * Fragment.startActivityForResult()}.
         *
         * @param fragment    your fragment.
         * @param requestCode request code.
         * @param options     options.
         */
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        public void startForResult(@NonNull Fragment fragment, int requestCode, @Nullable Bundle options) {
            fragment.startActivityForResult(build(), requestCode, options);
        }//startForResult()

        /**
         * Sets theme.
         *
         * @param resTheme see {@link #EXTRA_THEME}.
         * @return this builder.
         */
        @NonNull
        public <T extends IntentBuilder> T setTheme(@StyleRes int resTheme) {
            if (resTheme != 0)
                mIntent.putExtra(EXTRA_THEME, resTheme);
            else
                mIntent.removeExtra(EXTRA_THEME);

            return (T) this;
        }//setTheme()

        /**
         * Sets pattern.
         *
         * @param pattern see {@link #EXTRA_PATTERN}.
         * @return this builder.
         */
        @NonNull
        public <T extends IntentBuilder> T setPattern(@Nullable char[] pattern) {
            if (pattern != null)
                mIntent.putExtra(EXTRA_PATTERN, pattern);
            else
                mIntent.removeExtra(EXTRA_PATTERN);

            return (T) this;
        }//setPattern()

        /**
         * Sets result receiver.
         *
         * @param resultReceiver see {@link #EXTRA_RESULT_RECEIVER}.
         * @return this builder.
         */
        @NonNull
        public <T extends IntentBuilder> T setResultReceiver(@Nullable ResultReceiver resultReceiver) {
            if (resultReceiver != null)
                mIntent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiver);
            else
                mIntent.removeExtra(EXTRA_RESULT_RECEIVER);

            return (T) this;
        }//setResultReceiver()

        /**
         * Sets pending intent OK.
         *
         * @param pendingIntent see {@link #EXTRA_PENDING_INTENT_OK}.
         * @return this builder.
         */
        @NonNull
        public <T extends IntentBuilder> T setPendingIntentOk(@Nullable PendingIntent pendingIntent) {
            if (pendingIntent != null)
                mIntent.putExtra(EXTRA_PENDING_INTENT_OK, pendingIntent);
            else
                mIntent.removeExtra(EXTRA_PENDING_INTENT_OK);

            return (T) this;
        }//setPendingIntentOk()

        /**
         * Sets pending intent cancelled.
         *
         * @param pendingIntent see {@link #EXTRA_PENDING_INTENT_CANCELLED}.
         * @return this builder.
         */
        @NonNull
        public <T extends IntentBuilder> T setPendingIntentCancelled(@Nullable PendingIntent pendingIntent) {
            if (pendingIntent != null)
                mIntent.putExtra(EXTRA_PENDING_INTENT_CANCELLED, pendingIntent);
            else
                mIntent.removeExtra(EXTRA_PENDING_INTENT_CANCELLED);

            return (T) this;
        }//setPendingIntentCancelled()

        /**
         * Sets pending intent forgot pattern.
         *
         * @param pendingIntent see {@link #EXTRA_PENDING_INTENT_FORGOT_PATTERN}.
         * @return this builder.
         */
        @NonNull
        public <T extends IntentBuilder> T setPendingIntentForgotPattern(@Nullable PendingIntent pendingIntent) {
            if (pendingIntent != null)
                mIntent.putExtra(EXTRA_PENDING_INTENT_FORGOT_PATTERN, pendingIntent);
            else
                mIntent.removeExtra(EXTRA_PENDING_INTENT_FORGOT_PATTERN);

            return (T) this;
        }//setPendingIntentForgotPattern()

    }//IntentBuilder

    /**
     * Helper enum for button OK commands. (Because we use only one "OK" button for different commands).
     *
     * @author Hai Bison
     */
    private enum ButtonOkCommand {
        CONTINUE, FORGOT_PATTERN, DONE
    }// ButtonOkCommand

    /**
     * Delay time to reload the lock pattern view after a wrong pattern.
     */
    private static final long DELAY_TIME_TO_RELOAD_LOCK_PATTERN_VIEW = SECOND_IN_MILLIS;

    /////////
    // FIELDS
    /////////

    private int mMaxRetries, mMinWiredDots, mRetryCount = 0, mCaptchaWiredDots;
    private boolean mAutoSave, mStealthMode;
    private Encrypter mEncrypter;
    private ButtonOkCommand mBtnOkCmd;
    private Intent mIntentResult;
    private LoadingView<Void, Void, Object> mLoadingView;

    ///////////
    // CONTROLS
    ///////////

    private TextView mTextInfo;
    private LockPatternView mLockPatternView;
    private View mFooter;
    private Button mBtnConfirm, mBtnCancel;
    private View mViewGroupProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(CLASSNAME, "onCreate()");

        /**
         * EXTRA_THEME
         */
        if (getIntent().hasExtra(EXTRA_THEME))
            setTheme(getIntent().getIntExtra(EXTRA_THEME, R.style.Alp_42447968_Theme_Dark));

        /**
         * Apply theme resources
         */
        final int resThemeResources = ResourceUtils.resolveAttribute(this, R.attr.alp_42447968_theme_resources);
        if (resThemeResources == 0)
            throw new RuntimeException("Please provide theme resource via attribute `alp_42447968_theme_resources`."
                    + " For example: <item name=\"alp_42447968_theme_resources\">@style/Alp_42447968.ThemeResources.Light</item>");
        getTheme().applyStyle(resThemeResources, true);

        super.onCreate(savedInstanceState);

        loadSettings();

        mIntentResult = new Intent();
        setResult(RESULT_CANCELED, mIntentResult);

        initContentView();
    }// onCreate()

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (DEBUG) Log.d(CLASSNAME, "onConfigurationChanged()");

        initContentView();
    }// onConfigurationChanged()

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        /**
         * Use this hook instead of onBackPressed(), because onBackPressed() is not available in API 4.
         */
        if (keyCode == KeyEvent.KEYCODE_BACK && ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
            if (mLoadingView != null) mLoadingView.cancel(true);

            finishWithNegativeResult(RESULT_CANCELED);

            return true;
        }// if

        return super.onKeyDown(keyCode, event);
    }// onKeyDown()

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        /**
         * Support canceling dialog on touching outside in APIs < 11.
         *
         * This piece of code is copied from android.view.Window. You can find it by searching for methods shouldCloseOnTouch() and
         * isOutOfBounds().
         */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB && event.getAction() == MotionEvent.ACTION_DOWN
                && getWindow().peekDecorView() != null) {
            final int x = (int) event.getX();
            final int y = (int) event.getY();
            final int slop = ViewConfiguration.get(this).getScaledWindowTouchSlop();
            final View decorView = getWindow().getDecorView();
            boolean isOutOfBounds = (x < -slop) || (y < -slop) || (x > (decorView.getWidth() + slop))
                    || (y > (decorView.getHeight() + slop));
            if (isOutOfBounds) {
                finishWithNegativeResult(RESULT_CANCELED);
                return true;
            }
        }// if

        return super.onTouchEvent(event);
    }// onTouchEvent()

    @Override
    protected void onDestroy() {
        if (mLoadingView != null) mLoadingView.cancel(true);

        super.onDestroy();
    }// onDestroy()

    /**
     * Loads settings, either from manifest or {@link AlpSettings}.
     */
    private void loadSettings() {
        Bundle metaData = null;
        try {
            metaData = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA).metaData;
        } catch (NameNotFoundException e) {
            // Never catch this
            e.printStackTrace();
        }

        if (metaData != null && metaData.containsKey(METADATA_MIN_WIRED_DOTS))
            mMinWiredDots = AlpSettings.Display.validateMinWiredDots(this, metaData.getInt(METADATA_MIN_WIRED_DOTS));
        else
            mMinWiredDots = AlpSettings.Display.getMinWiredDots(this);

        if (metaData != null && metaData.containsKey(METADATA_MAX_RETRIES))
            mMaxRetries = AlpSettings.Display.validateMaxRetries(this, metaData.getInt(METADATA_MAX_RETRIES));
        else
            mMaxRetries = AlpSettings.Display.getMaxRetries(this);

        if (metaData != null && metaData.containsKey(METADATA_AUTO_SAVE_PATTERN))
            mAutoSave = metaData.getBoolean(METADATA_AUTO_SAVE_PATTERN);
        else
            mAutoSave = AlpSettings.Security.isAutoSavePattern(this);

        if (metaData != null && metaData.containsKey(METADATA_CAPTCHA_WIRED_DOTS))
            mCaptchaWiredDots = AlpSettings.Display.validateCaptchaWiredDots(this, metaData.getInt(METADATA_CAPTCHA_WIRED_DOTS));
        else
            mCaptchaWiredDots = AlpSettings.Display.getCaptchaWiredDots(this);

        if (metaData != null && metaData.containsKey(METADATA_STEALTH_MODE))
            mStealthMode = metaData.getBoolean(METADATA_STEALTH_MODE);
        else
            mStealthMode = AlpSettings.Display.isStealthMode(this);

        /**
         * Encrypter.
         */
        char[] encrypterClass;
        if (metaData != null && metaData.containsKey(METADATA_ENCRYPTER_CLASS))
            encrypterClass = metaData.getString(METADATA_ENCRYPTER_CLASS).toCharArray();
        else
            encrypterClass = AlpSettings.Security.getEncrypterClass(this);

        if (encrypterClass != null) {
            try {
                mEncrypter = (Encrypter) Class.forName(new String(encrypterClass), false, getClassLoader()).newInstance();
            } catch (Throwable t) {
                throw new InvalidEncrypterException();
            }
        }
    }// loadSettings()

    /**
     * Initializes UI...
     */
    private void initContentView() {
        /**
         * Save all controls' state to restore later.
         */
        CharSequence infoText = mTextInfo != null ? mTextInfo.getText() : null;
        Boolean btnOkEnabled = mBtnConfirm != null ? mBtnConfirm.isEnabled() : null;
        LockPatternView.DisplayMode lastDisplayMode = mLockPatternView != null ? mLockPatternView.getDisplayMode() : null;
        List<Cell> lastPattern = mLockPatternView != null ? mLockPatternView.getPattern() : null;

        setContentView(R.layout.alp_42447968_lock_pattern_activity);
        UI.adjustDialogSizeForLargeScreens(getWindow());

        /**
         * MAP CONTROLS
         */

        mTextInfo = (TextView) findViewById(R.id.alp_42447968_textview_info);
        mLockPatternView = (LockPatternView) findViewById(R.id.alp_42447968_view_lock_pattern);

        mFooter = findViewById(R.id.alp_42447968_viewgroup_footer);
        mBtnCancel = (Button) findViewById(R.id.alp_42447968_button_cancel);
        mBtnConfirm = (Button) findViewById(R.id.alp_42447968_button_confirm);

        mViewGroupProgressBar = findViewById(R.id.alp_42447968_view_group_progress_bar);

        /**
         * SETUP CONTROLS
         */

        mViewGroupProgressBar.setOnClickListener(mViewGroupProgressBarOnClickListener);

        /**
         * LOCK PATTERN VIEW
         */

        switch (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) {
        case Configuration.SCREENLAYOUT_SIZE_LARGE:
        case Configuration.SCREENLAYOUT_SIZE_XLARGE: {
            final int size = getResources().getDimensionPixelSize(R.dimen.alp_42447968_lockpatternview_size);
            LayoutParams lp = mLockPatternView.getLayoutParams();
            lp.width = size;
            lp.height = size;
            mLockPatternView.setLayoutParams(lp);

            break;
        }// LARGE / XLARGE
        }

        // Haptic feedback
        boolean hapticFeedbackEnabled = false;
        try {
            /**
             * This call requires permission WRITE_SETTINGS. Since it's not necessary, we don't need to declare that permission in manifest.
             * Don't scare our users  :-D
             */
            hapticFeedbackEnabled = Settings.System.getInt(getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0;
        } catch (Throwable t) {
            // Ignore it
            t.printStackTrace();
        }
        mLockPatternView.setTactileFeedbackEnabled(hapticFeedbackEnabled);

        mLockPatternView.setInStealthMode(mStealthMode && !ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction()));
        mLockPatternView.setOnPatternListener(mLockPatternViewListener);
        if (lastPattern != null && lastDisplayMode != null && !ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction()))
            mLockPatternView.setPattern(lastDisplayMode, lastPattern);

        /**
         * COMMAND BUTTONS
         */

        if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
            mBtnCancel.setOnClickListener(mBtnCancelOnClickListener);
            mBtnConfirm.setOnClickListener(mBtnConfirmOnClickListener);

            mBtnCancel.setVisibility(View.VISIBLE);
            mFooter.setVisibility(View.VISIBLE);

            if (infoText != null)
                mTextInfo.setText(infoText);
            else
                mTextInfo.setText(R.string.alp_42447968_msg_draw_an_unlock_pattern);

            /**
             * BUTTON OK
             */
            if (mBtnOkCmd == null) mBtnOkCmd = ButtonOkCommand.CONTINUE;
            switch (mBtnOkCmd) {
            case CONTINUE:
                mBtnConfirm.setText(R.string.alp_42447968_cmd_continue);
                break;
            case DONE:
                mBtnConfirm.setText(R.string.alp_42447968_cmd_confirm);
                break;
            default:
                /**
                 * Do nothing.
                 */
                break;
            }
            if (btnOkEnabled != null) mBtnConfirm.setEnabled(btnOkEnabled);
        }// ACTION_CREATE_PATTERN
        else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
            if (TextUtils.isEmpty(infoText))
                mTextInfo.setText(R.string.alp_42447968_msg_draw_pattern_to_unlock);
            else
                mTextInfo.setText(infoText);
            if (getIntent().hasExtra(EXTRA_PENDING_INTENT_FORGOT_PATTERN)) {
                mBtnConfirm.setOnClickListener(mBtnConfirmOnClickListener);
                mBtnConfirm.setText(R.string.alp_42447968_cmd_forgot_pattern);
                mBtnConfirm.setEnabled(true);
                mFooter.setVisibility(View.VISIBLE);
            }
        }// ACTION_COMPARE_PATTERN
        else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
            mTextInfo.setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);

            /**
             * NOTE: EXTRA_PATTERN should hold a char[] array. In this case we use it as a temporary variable to hold a list of Cell.
             */

            final ArrayList<Cell> pattern;
            if (getIntent().hasExtra(EXTRA_PATTERN))
                pattern = getIntent().getParcelableArrayListExtra(EXTRA_PATTERN);
            else
                getIntent().putParcelableArrayListExtra(EXTRA_PATTERN, pattern = LockPatternUtils.genCaptchaPattern(mCaptchaWiredDots));

            mLockPatternView.setPattern(DisplayMode.Animate, pattern);
        }// ACTION_VERIFY_CAPTCHA
    }// initContentView()

    /**
     * Compares {@code pattern} to the given pattern ( {@link #ACTION_COMPARE_PATTERN}) or to the generated "CAPTCHA"
     * pattern ( {@link #ACTION_VERIFY_CAPTCHA}). Then finishes the activity if they match.
     *
     * @param pattern the pattern to be compared.
     */
    private void doComparePattern(@NonNull final List<Cell> pattern) {
        if (pattern == null) return;

        /**
         * Use a LoadingView because decrypting pattern might take time...
         */

        mLoadingView = new LoadingView<Void, Void, Object>(this, mViewGroupProgressBar) {

            @Override
            protected Object doInBackground(Void... params) {
                if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
                    char[] currentPattern = getIntent().getCharArrayExtra(EXTRA_PATTERN);
                    if (currentPattern == null)
                        currentPattern = AlpSettings.Security.getPattern(LockPatternActivity.this);
                    if (currentPattern != null) {
                        if (mEncrypter != null)
                            return pattern.equals(mEncrypter.decrypt(LockPatternActivity.this, currentPattern));
                        else
                            return Arrays.equals(currentPattern, LockPatternUtils.patternToSha1(pattern).toCharArray());
                    }
                }// ACTION_COMPARE_PATTERN
                else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
                    return pattern.equals(getIntent().getParcelableArrayListExtra(EXTRA_PATTERN));
                }// ACTION_VERIFY_CAPTCHA

                return false;
            }// doInBackground()

            @Override
            protected void onPostExecute(Object result) {
                super.onPostExecute(result);

                if ((Boolean) result) finishWithResultOk(null);
                else {
                    mRetryCount++;
                    mIntentResult.putExtra(EXTRA_RETRY_COUNT, mRetryCount);

                    if (mRetryCount >= mMaxRetries)
                        finishWithNegativeResult(RESULT_FAILED);
                    else {
                        mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                        mTextInfo.setText(R.string.alp_42447968_msg_try_again);
                        mLockPatternView.postDelayed(mLockPatternViewReloader, DELAY_TIME_TO_RELOAD_LOCK_PATTERN_VIEW);
                    }
                }
            }// onPostExecute()

        };

        mLoadingView.execute();
    }// doComparePattern()

    /**
     * Checks and creates the pattern.
     *
     * @param pattern the current pattern of lock pattern view.
     */
    private void doCheckAndCreatePattern(@NonNull final List<Cell> pattern) {
        if (pattern.size() < mMinWiredDots) {
            mLockPatternView.setDisplayMode(DisplayMode.Wrong);
            mTextInfo.setText(getResources().getQuantityString(R.plurals.alp_42447968_pmsg_connect_x_dots, mMinWiredDots, mMinWiredDots));
            mLockPatternView.postDelayed(mLockPatternViewReloader, DELAY_TIME_TO_RELOAD_LOCK_PATTERN_VIEW);
            return;
        }// if

        if (getIntent().hasExtra(EXTRA_PATTERN)) {
            /**
             * Use a LoadingView because decrypting pattern might take time...
             */
            mLoadingView = new LoadingView<Void, Void, Object>(this, mViewGroupProgressBar) {

                @Override
                protected Object doInBackground(Void... params) {
                    if (mEncrypter != null)
                        return pattern.equals(mEncrypter.decrypt(LockPatternActivity.this, getIntent().getCharArrayExtra(EXTRA_PATTERN)));
                    else
                        return Arrays.equals(
                                getIntent().getCharArrayExtra(EXTRA_PATTERN),
                                LockPatternUtils.patternToSha1(pattern).toCharArray());
                }// doInBackground()

                @Override
                protected void onPostExecute(Object result) {
                    super.onPostExecute(result);

                    if ((Boolean) result) {
                        mTextInfo.setText(R.string.alp_42447968_msg_your_new_unlock_pattern);
                        mBtnConfirm.setEnabled(true);
                    } else {
                        mTextInfo.setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
                        mBtnConfirm.setEnabled(false);
                        mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                        mLockPatternView.postDelayed(mLockPatternViewReloader, DELAY_TIME_TO_RELOAD_LOCK_PATTERN_VIEW);
                    }
                }// onPostExecute()

            };

            mLoadingView.execute();
        } else {
            /**
             * Use a LoadingView because encrypting pattern might take time...
             */
            mLoadingView = new LoadingView<Void, Void, Object>(this, mViewGroupProgressBar) {

                @Override
                protected Object doInBackground(Void... params) {
                    return mEncrypter != null ?
                            mEncrypter.encrypt(LockPatternActivity.this, pattern) :
                            LockPatternUtils.patternToSha1(pattern).toCharArray();
                }// onCancel()

                @Override
                protected void onPostExecute(Object result) {
                    super.onPostExecute(result);

                    getIntent().putExtra(EXTRA_PATTERN, (char[]) result);
                    mTextInfo.setText(R.string.alp_42447968_msg_pattern_recorded);
                    mBtnConfirm.setEnabled(true);
                }// onPostExecute()

            };

            mLoadingView.execute();
        }
    }// doCheckAndCreatePattern()

    /**
     * Finishes activity with {@link Activity#RESULT_OK}.
     *
     * @param pattern the pattern, if this is in mode creating pattern. In any cases, it can be set to {@code null}.
     */
    private void finishWithResultOk(@Nullable char[] pattern) {
        if (ACTION_CREATE_PATTERN.equals(getIntent().getAction()))
            mIntentResult.putExtra(EXTRA_PATTERN, pattern);
        else {
            /**
             * If the user was "logging in", minimum try count can not be zero.
             */
            mIntentResult.putExtra(EXTRA_RETRY_COUNT, mRetryCount + 1);
        }

        setResult(RESULT_OK, mIntentResult);

        /**
         * ResultReceiver
         */
        ResultReceiver receiver = getIntent().getParcelableExtra(EXTRA_RESULT_RECEIVER);
        if (receiver != null) {
            Bundle bundle = new Bundle();
            if (ACTION_CREATE_PATTERN.equals(getIntent().getAction()))
                bundle.putCharArray(EXTRA_PATTERN, pattern);
            else {
                /**
                 * If the user was "logging in", minimum try count can not be zero.
                 */
                bundle.putInt(EXTRA_RETRY_COUNT, mRetryCount + 1);
            }
            receiver.send(RESULT_OK, bundle);
        }

        /**
         * PendingIntent
         */
        PendingIntent pi = getIntent().getParcelableExtra(EXTRA_PENDING_INTENT_OK);
        if (pi != null) {
            try {
                pi.send(this, RESULT_OK, mIntentResult);
            } catch (Throwable t) {
                Log.e(CLASSNAME, "Error sending PendingIntent: " + pi, t);
            }
        }//if

        finish();
    }// finishWithResultOk()

    /**
     * Finishes the activity with negative result ( {@link Activity#RESULT_CANCELED}, {@link #RESULT_FAILED} or {@link
     * #RESULT_FORGOT_PATTERN}).
     */
    private void finishWithNegativeResult(int resultCode) {
        if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction()))
            mIntentResult.putExtra(EXTRA_RETRY_COUNT, mRetryCount);

        setResult(resultCode, mIntentResult);

        /**
         * ResultReceiver
         */
        ResultReceiver receiver = getIntent().getParcelableExtra(EXTRA_RESULT_RECEIVER);
        if (receiver != null) {
            Bundle resultBundle = null;
            if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
                resultBundle = new Bundle();
                resultBundle.putInt(EXTRA_RETRY_COUNT, mRetryCount);
            }
            receiver.send(resultCode, resultBundle);
        }//if

        /**
         * PendingIntent
         */
        PendingIntent pi = getIntent().getParcelableExtra(EXTRA_PENDING_INTENT_CANCELLED);
        if (pi != null) {
            try {
                pi.send(this, resultCode, mIntentResult);
            } catch (Throwable t) {
                Log.e(CLASSNAME, "Error sending PendingIntent: " + pi, t);
            }
        }//if

        finish();
    }// finishWithNegativeResult()

    ////////////
    // LISTENERS
    ////////////

    /**
     * Pattern listener for LockPatternView.
     */
    private final LockPatternView.OnPatternListener mLockPatternViewListener = new LockPatternView.OnPatternListener() {

        @Override
        public void onPatternStart() {
            mLockPatternView.removeCallbacks(mLockPatternViewReloader);
            mLockPatternView.setDisplayMode(DisplayMode.Correct);

            if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
                mTextInfo.setText(R.string.alp_42447968_msg_release_finger_when_done);
                mBtnConfirm.setEnabled(false);
                if (mBtnOkCmd == ButtonOkCommand.CONTINUE) getIntent().removeExtra(EXTRA_PATTERN);
            }// ACTION_CREATE_PATTERN
            else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
                mTextInfo.setText(R.string.alp_42447968_msg_draw_pattern_to_unlock);
            }// ACTION_COMPARE_PATTERN
            else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
                mTextInfo.setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
            }// ACTION_VERIFY_CAPTCHA
        }// onPatternStart()

        @Override
        public void onPatternDetected(List<Cell> pattern) {
            if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
                doCheckAndCreatePattern(pattern);
            }// ACTION_CREATE_PATTERN
            else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
                doComparePattern(pattern);
            }// ACTION_COMPARE_PATTERN
            else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
                if (!DisplayMode.Animate.equals(mLockPatternView.getDisplayMode()))
                    doComparePattern(pattern);
            }// ACTION_VERIFY_CAPTCHA
        }// onPatternDetected()

        @Override
        public void onPatternCleared() {
            mLockPatternView.removeCallbacks(mLockPatternViewReloader);

            if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
                mLockPatternView.setDisplayMode(DisplayMode.Correct);
                mBtnConfirm.setEnabled(false);
                if (mBtnOkCmd == ButtonOkCommand.CONTINUE) {
                    getIntent().removeExtra(EXTRA_PATTERN);
                    mTextInfo.setText(R.string.alp_42447968_msg_draw_an_unlock_pattern);
                } else
                    mTextInfo.setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
            }// ACTION_CREATE_PATTERN
            else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
                mLockPatternView.setDisplayMode(DisplayMode.Correct);
                mTextInfo.setText(R.string.alp_42447968_msg_draw_pattern_to_unlock);
            }// ACTION_COMPARE_PATTERN
            else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
                mTextInfo.setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
                List<Cell> pattern = getIntent().getParcelableArrayListExtra(EXTRA_PATTERN);
                mLockPatternView.setPattern(DisplayMode.Animate, pattern);
            }// ACTION_VERIFY_CAPTCHA
        }// onPatternCleared()

        @Override
        public void onPatternCellAdded(List<Cell> pattern) {
            // Nothing to do
        }// onPatternCellAdded()

    };// mLockPatternViewListener

    /**
     * Click listener for button Cancel.
     */
    private final View.OnClickListener mBtnCancelOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            finishWithNegativeResult(RESULT_CANCELED);
        }// onClick()

    };// mBtnCancelOnClickListener

    /**
     * Click listener for button Confirm.
     */
    private final View.OnClickListener mBtnConfirmOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
                if (mBtnOkCmd == ButtonOkCommand.CONTINUE) {
                    mBtnOkCmd = ButtonOkCommand.DONE;
                    mLockPatternView.clearPattern();
                    mTextInfo.setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
                    mBtnConfirm.setText(R.string.alp_42447968_cmd_confirm);
                    mBtnConfirm.setEnabled(false);
                } else {
                    final char[] pattern = getIntent().getCharArrayExtra(EXTRA_PATTERN);
                    if (mAutoSave) AlpSettings.Security.setPattern(LockPatternActivity.this, pattern);
                    finishWithResultOk(pattern);
                }
            }// ACTION_CREATE_PATTERN
            else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
                /**
                 * We don't need to verify the extra. First, this button is only visible if there is this extra in the intent. Second, it is the
                 * responsibility of the caller to make sure the extra is good.
                 */
                PendingIntent pi = null;
                try {
                    pi = getIntent().getParcelableExtra(EXTRA_PENDING_INTENT_FORGOT_PATTERN);
                    if (pi != null) pi.send();
                } catch (Throwable t) {
                    Log.e(CLASSNAME, "Error sending pending intent: " + pi, t);
                }
                finishWithNegativeResult(RESULT_FORGOT_PATTERN);
            }// ACTION_COMPARE_PATTERN
        }// onClick()

    };// mBtnConfirmOnClickListener

    /**
     * This reloads the {@link #mLockPatternView} after a wrong pattern.
     */
    private final Runnable mLockPatternViewReloader = new Runnable() {

        @Override
        public void run() {
            mLockPatternView.clearPattern();
            mLockPatternViewListener.onPatternCleared();
        }// run()

    };// mLockPatternViewReloader

    /**
     * Click listener for view group progress bar.
     */
    private final View.OnClickListener mViewGroupProgressBarOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            /**
             * Do nothing. We just don't want the user to interact with controls behind this view.
             */
        }// onClick()

    };// mViewGroupProgressBarOnClickListener

}
