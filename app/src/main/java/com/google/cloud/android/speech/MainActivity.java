/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.android.speech;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements MessageDialogFragment.Listener {

    private static final String FRAGMENT_MESSAGE_DIALOG = "message_dialog";
    private static final int MENU_PLAY = 1000;
    private static final String STATE_RESULTS = "results";

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private static final String LOG_TAG_DEBUG = "MainActivity";
    private static int WordCountInterval = 5;
    private static int minimum_words_vibration = 10;
    String[] languages;
    String[] languageValues;
    private String recordingLangCode;
    private String recordingLangName;

    private SpeechService mSpeechService;
    private StringBuilder speechTextBuilder;
    private VoiceRecorder mVoiceRecorder;
    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

        @Override
        public void onVoiceStart() {
            showStatus(true);
            if (mSpeechService != null) {
                mSpeechService.startRecognizing(mVoiceRecorder.getSampleRate(), recordingLangCode);
            }
        }

        @Override
        public void onVoice(byte[] data, int size) {
            if (mSpeechService != null) {
                mSpeechService.recognize(data, size);
            }
        }

        @Override
        public void onVoiceEnd() {
            showStatus(false);
            if (mSpeechService != null) {
                mSpeechService.finishRecognizing();
            }
        }

    };

    // Resource caches
    private int mColorHearing;
    private int mColorNotHearing;
    private int WordCountIntervalIncrementor = 5;
    private TextView speechTextView;
    // View references
//    private TextView mStatus;
//    private TextView mText;
//    private ResultAdapter mAdapter;
//    private TextView resultsTextView;
//    private RecyclerView mRecyclerView;
    private ImageView startRecordingButton, stopRecordingButton;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mSpeechService = SpeechService.from(binder);
//            mStatus.setVisibility(View.VISIBLE);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSpeechService = null;
        }

    };
    private boolean isRecording;
    private TextView avgWordCountTextView;
    private long intervalSpeechStartDate, intervalSpeechStopDate, totalSpeechTime;
    private final SpeechService.Listener mSpeechServiceListener =
            new SpeechService.Listener() {
                @Override
                public void onSpeechRecognized(final String text, final boolean isFinal) {
                    intervalSpeechStopDate = System.currentTimeMillis();
                    if (isFinal) {
                        totalSpeechTime = intervalSpeechStopDate - intervalSpeechStartDate;
                        mVoiceRecorder.dismiss();
                    }
                    Log.i("MainActivity", text);
//                    if (mText != null && !TextUtils.isEmpty(text)) {
                    if (!TextUtils.isEmpty(text)) {
                        totalSpeechTime = intervalSpeechStopDate - intervalSpeechStartDate;
                        new CalculateAvgWordCountTask(MainActivity.this).execute();
                        new CalculateKeywordCountTask(MainActivity.this).execute();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
//                                if (isFinal) {
//                                    mText.setText(null);
////                                    resultsTextView.setText(resultsTextView.getText()+"\n"+text);
//                                    mAdapter.addResult(text);
//                                    mRecyclerView.smoothScrollToPosition(0);
//                                } else {
//                                    mText.setText(text);
//                                }
                                if (isFinal) {

                                    if (speechTextBuilder.toString() != "")
                                        speechTextBuilder.append("\n");
                                    speechTextBuilder.append(text);
                                    speechTextView.setText(speechTextBuilder.toString());
                                } else {
                                    if (speechTextBuilder.toString() == "")
                                        speechTextView.setText(text);
                                    else
                                        speechTextView.setText(speechTextBuilder.toString() + "\n" + text);
                                }
                            }
                        });
                    }
                }
            };
    private TextView recordingLangHeadingTextView;
    private TextView speakingLangHeadingTextView;
    private TextView keywordValueTextView;
    private TextView errorTextView;
    private TextView keywordTextView;
    private String keyword;
    private TextToSpeech textToSpeech;
    private Boolean readyToSpeak = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);
        startRecordingButton = findViewById(R.id.startRecordingButton);
        stopRecordingButton = findViewById(R.id.stopRecordingButton);
        speechTextView = findViewById(R.id.speechTextView);
        recordingLangHeadingTextView = findViewById(R.id.recordingLanguageHeading);
        speakingLangHeadingTextView = findViewById(R.id.speakingLanguageHeading);
        avgWordCountTextView = findViewById(R.id.avgWordCountTextView);
        keywordValueTextView = findViewById(R.id.keywordValueTextView);
        errorTextView = findViewById(R.id.errorTextView);
        keywordTextView = findViewById(R.id.keywordTextView);

        languages = getResources().getStringArray(R.array.languages);
        languageValues = getResources().getStringArray(R.array.languages_values);
//        speechTextView.setMovementMethod(new ScrollingMovementMethod());
//        final Resources resources = getResources();
//        final Resources.Theme theme = getTheme();
//        mColorHearing = ResourcesCompat.getColor(resources, R.color.status_hearing, theme);
//        mColorNotHearing = ResourcesCompat.getColor(resources, R.color.status_not_hearing, theme);
        setSupportActionBar((Toolbar) findViewById(R.id.my_toolbar));
//        mStatus = (TextView) findViewById(R.id.status);
//        mText = (TextView) findViewById(R.id.text);
//        resultsTextView=findViewById(R.id.resultsTextView);
//        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
//        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
//        final ArrayList<String> results = savedInstanceState == null ? null :
//                savedInstanceState.getStringArrayList(STATE_RESULTS);
//        mAdapter = new ResultAdapter(results);
//        mRecyclerView.setAdapter(mAdapter);
        startRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecordButtonClick();
            }
        });
        stopRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecordButtonClick();
            }
        });
        isRecording = false;
        LoadSupportedLanguages(this);
        speechTextView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(Menu.NONE, MENU_PLAY, 1, "Pronounce word").setIcon(R.drawable.ic_play_sound);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                // Remove the "select all" option
                menu.removeItem(android.R.id.selectAll);
                // Remove the "cut" option
                menu.removeItem(android.R.id.cut);
                // Remove the "copy all" option
                menu.removeItem(android.R.id.copy);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == MENU_PLAY) {
                    int min = 0;
                    int max = speechTextView.getText().length();
                    if (speechTextView.isFocused()) {
                        final int selStart = speechTextView.getSelectionStart();
                        final int selEnd = speechTextView.getSelectionEnd();

                        min = Math.max(0, Math.min(selStart, selEnd));
                        max = Math.max(0, Math.max(selStart, selEnd));
                    }
                    // Perform your definition lookup with the selected text
                    final CharSequence selectedText = speechTextView.getText().subSequence(min, max);
                    PlayTextToSpeech(selectedText.toString());
                    // Finish and close the ActionMode
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {

            }
        });
    }

    protected void startRecordButtonClick() {
        Log.d(LOG_TAG_DEBUG, "startRecordButtonClick");
        // Start listening to voices
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecorder();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Prepare Cloud Speech API
        bindService(new Intent(this, SpeechService.class), mServiceConnection, BIND_AUTO_CREATE);

        // Start listening to voices
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
//                == PackageManager.PERMISSION_GRANTED) {
//            startVoiceRecorder();
//        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
//                Manifest.permission.RECORD_AUDIO)) {
//            showPermissionMessageDialog();
//        } else {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
//                    REQUEST_RECORD_AUDIO_PERMISSION);
//        }
    }

    protected void stopRecordButtonClick() {
        Log.d(LOG_TAG_DEBUG, "stopRecordButtonClick");
        stopVoiceRecorder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        recordingLangCode = preferences.getString("languages", "en-US");
        recordingLangName = getRecordingLangName(recordingLangCode);
        recordingLangHeadingTextView.setText(recordingLangName);
        String speakingLanguage = preferences.getString("speakinglanguages", "en-US");
        String speakingLanguageName = Locale.forLanguageTag(speakingLanguage).getDisplayName();
        speakingLangHeadingTextView.setText(speakingLanguageName);
        keyword = preferences.getString("keyword", "Not set");
        keywordValueTextView.setText(keyword);
        WordCountInterval = Integer.parseInt(preferences.getString("word_count_interval", "5"));
        minimum_words_vibration = Integer.parseInt(preferences.getString("minimum_words_vibration", getString(R.string.minimum_words_vibration)));
        new PrepareTextToSpeechTask(this).execute();
    }

    private String getRecordingLangName(String langCode) {
        Log.d(LOG_TAG_DEBUG, "Method: getRecordingLangName:" + langCode);
        Log.d(LOG_TAG_DEBUG, languageValues.toString());
        int langValueIndex = Arrays.asList(languageValues).indexOf(langCode);
        return languages[langValueIndex];
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopVoiceRecorder();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
//        if (mAdapter != null) {
//            outState.putStringArrayList(STATE_RESULTS, mAdapter.getResults());
//        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (permissions.length == 1 && grantResults.length == 1
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecorder();
            } else {
                showPermissionMessageDialog();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onStop() {
        // Stop listening to voice
        stopVoiceRecorder();

        // Stop Cloud Speech API
        if (mSpeechService != null) {
            mSpeechService.removeAllListeners();
            unbindService(mServiceConnection);
            mSpeechService = null;
        }
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
//            case R.id.action_file:
//                mSpeechService.recognizeInputStream(getResources().openRawResource(R.raw.audio));
//                return true;
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_playAudio:
                PlayTextToSpeech(speechTextView.getText().toString());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startVoiceRecorder() {
        Log.d(LOG_TAG_DEBUG, "startVoiceRecorder");
        isRecording = true;
        startRecordingButton.setVisibility(View.INVISIBLE);
        stopRecordingButton.setVisibility(View.VISIBLE);

        if (!mSpeechService.hasListeners()) {
            mSpeechService.addListener(mSpeechServiceListener);
        }
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
        }
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();

        speechTextBuilder = new StringBuilder();
        speechTextView.setText(null);
        intervalSpeechStartDate = System.currentTimeMillis();
        totalSpeechTime = 0;
        keyword = "hello";
    }

    private void showPermissionMessageDialog() {
        MessageDialogFragment
                .newInstance(getString(R.string.permission_message))
                .show(getSupportFragmentManager(), FRAGMENT_MESSAGE_DIALOG);
    }

    private void showStatus(final boolean hearingVoice) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                mStatus.setTextColor(hearingVoice ? mColorHearing : mColorNotHearing);
            }
        });
    }

    @Override
    public void onMessageDialogDismissed() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);
    }

    private void stopVoiceRecorder() {
        startRecordingButton.setVisibility(View.VISIBLE);
        stopRecordingButton.setVisibility(View.INVISIBLE);
        if (mSpeechService != null) {
            mSpeechService.removeAllListeners();
        }
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
        isRecording = false;
    }

//    @Override
//    public void onClick(View v) {
//        Log.d(LOG_TAG_DEBUG,"onClick");
//        switch (v.getId()) {
//            case R.id.startRecordingButton:
//                startRecordButtonClick();
//                break;
//            case R.id.stopRecordingButton:
//                stopRecordButtonClick();
//                break;
//            default:
//                return;
//        }
//    }


//    private static class ViewHolder extends RecyclerView.ViewHolder {
//
//        TextView text;
//
//        ViewHolder(LayoutInflater inflater, ViewGroup parent) {
//            super(inflater.inflate(R.layout.item_result, parent, false));
//            text = (TextView) itemView.findViewById(R.id.text);
//        }
//
//    }
//
//    private static class ResultAdapter extends RecyclerView.Adapter<ViewHolder> {
//
//        private final ArrayList<String> mResults = new ArrayList<>();
//
//        ResultAdapter(ArrayList<String> results) {
//            if (results != null) {
//                mResults.addAll(results);
//            }
//        }
//
//        @Override
//        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
//            return new ViewHolder(LayoutInflater.from(parent.getContext()), parent);
//        }
//
//        @Override
//        public void onBindViewHolder(ViewHolder holder, int position) {
//            holder.text.setText(mResults.get(position));
//        }
//
//        @Override
//        public int getItemCount() {
//            return mResults.size();
//        }
//
//        void addResult(String result) {
//            mResults.add(0, result);
//            notifyItemInserted(0);
//        }
//
//        public ArrayList<String> getResults() {
//            return mResults;
//        }
//
//    }

    private void LoadSupportedLanguages(final Context context) {
        final TextToSpeech[] textToSpeech1 = new TextToSpeech[1];
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                if (!preferences.contains("langNames") || !preferences.contains("langCodes")) {
                    final List<String> langCodes = new LinkedList<String>();
                    final List<String> langNames = new LinkedList<String>();
                    textToSpeech1[0] = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            Log.d(LOG_TAG_DEBUG, "Method: ASYNC onInit");
                            if (status == TextToSpeech.SUCCESS) {
                                Set<Locale> languages = textToSpeech1[0].getAvailableLanguages();
                                String speakingLang = "";
                                List<Locale> sortedLanguages = new ArrayList<Locale>(languages);
//                                Log.i(LOG_TAG_DEBUG, String.valueOf(sortedLanguages.size()));
                                Collections.sort(sortedLanguages, new Comparator<Locale>() {
                                    @Override
                                    public int compare(Locale o1, Locale o2) {
                                        return o1.getDisplayName().compareTo(o2.getDisplayName());
                                    }
                                });

                                for (Locale lang : sortedLanguages) {
//                                    Log.d(LOG_TAG_DEBUG, lang.toString());
                                    langCodes.add(lang.toLanguageTag());
                                    langNames.add(lang.getDisplayName());
                                }
                                SharedPreferences sharedPreferences = context.getApplicationContext().getSharedPreferences("SPEECH_RECOGNIZER", MODE_PRIVATE);
                                SharedPreferences.Editor editor = sharedPreferences.edit();
//                                Log.i(LOG_TAG_DEBUG, String.valueOf(langNames.size()));
//                                Log.i(LOG_TAG_DEBUG, String.valueOf(langCodes.size()));
                                editor.putString("langNames", TextUtils.join(",", langNames));
                                editor.putString("langCodes", TextUtils.join(",", langCodes));
                                editor.apply();
                            }
                        }
                    });
                }
            }
        };
        new Thread(runnable).start();
    }

    private void PlayTextToSpeech(String returnedText) {
        if (isRecording) {
            Toast.makeText(getApplicationContext(), "Recording in Progress. Please click on Stop Recording before clicking on Listen button", Toast.LENGTH_SHORT).show();
        }
        if (readyToSpeak && !Objects.equals(returnedText, "")) {
            Log.d(LOG_TAG_DEBUG, "Ready to speak");
            String toSpeak = returnedText;
            Toast.makeText(getApplicationContext(), toSpeak, Toast.LENGTH_SHORT).show();
            Bundle bundle = new Bundle();
            bundle.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
            try {
                textToSpeech.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, bundle, UUID.randomUUID().toString());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            Log.d(LOG_TAG_DEBUG, "returnedText:" + returnedText);
            Log.d(LOG_TAG_DEBUG, "readyToSpeak:" + readyToSpeak.toString());
            Toast.makeText(getApplicationContext(), "No text present for speech.", Toast.LENGTH_SHORT).show();
            Log.w(LOG_TAG_DEBUG, "No text present for speech");
        }
    }

    private static class CalculateAvgWordCountTask extends AsyncTask<Void, Integer, Long> {

        private final WeakReference<MainActivity> mActivityRef;

        public CalculateAvgWordCountTask(MainActivity activity) {
            mActivityRef = new WeakReference<>(activity);
        }

        @Override
        protected Long doInBackground(Void... voids) {
            String partialFinalResult = mActivityRef.get().speechTextView.getText().toString();
            Log.d(LOG_TAG_DEBUG, "Partial Final Result:" + partialFinalResult);
            long intervalTimeInMS = mActivityRef.get().intervalSpeechStopDate - mActivityRef.get().intervalSpeechStartDate;
            long temporaryTotalSpeechTime = intervalTimeInMS / 1000;
            Log.d(LOG_TAG_DEBUG, "Temporary Total Speech Time: " + temporaryTotalSpeechTime);
            if (temporaryTotalSpeechTime >= mActivityRef.get().WordCountIntervalIncrementor) {
//                CalculateAvgWordCount(temporaryTotalSpeechTime, partialFinalResult);
                Log.d(LOG_TAG_DEBUG, "Method: CalculateAvgWordCount");
                int wordCount = countWordsUsingSplit(partialFinalResult);
                Log.d(LOG_TAG_DEBUG, "Word Count:" + Integer.toString(wordCount));
                long avgWordCount = wordCount / (temporaryTotalSpeechTime / mActivityRef.get().WordCountInterval);
                Log.d(LOG_TAG_DEBUG, "Avg Word Count:" + Long.toString(avgWordCount));
                return avgWordCount;
            }
            return null;
        }

        private int countWordsUsingSplit(String input) {
            //Log.d(LOG_TAG_DEBUG,"countWordsUsingSplit");
            if (input == null || input.isEmpty()) {
                return 0;
            }
            String[] words = input.split("\\s+");
            return words.length;
        }

        @Override
        protected void onPostExecute(Long avgWordCount) {
            super.onPostExecute(avgWordCount);
            if (avgWordCount != null) {
                if (avgWordCount > mActivityRef.get().minimum_words_vibration) {
                    Vibrator v = (Vibrator) mActivityRef.get().getSystemService(Context.VIBRATOR_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Objects.requireNonNull(v, "Vibrator service is returning as null.").vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        Objects.requireNonNull(v, "Vibrator service is returning as null.").vibrate(500);
                    }
                }
                mActivityRef.get().WordCountIntervalIncrementor = mActivityRef.get().WordCountIntervalIncrementor + mActivityRef.get().WordCountInterval;
                mActivityRef.get().avgWordCountTextView.setText(Long.toString(avgWordCount) + " words per " + Integer.toString(WordCountInterval) + " seconds.");
            }
        }
    }

    private static class CalculateKeywordCountTask extends AsyncTask<Void, Integer, Integer> {
        private final WeakReference<MainActivity> mActivityRef;

        public CalculateKeywordCountTask(MainActivity activity) {
            mActivityRef = new WeakReference<>(activity);
        }

        public static int CountOfSubstringInString(String string, String substring) {
            //Log.d(LOG_TAG_DEBUG,"CountOfSubstringInString");
            int count = 0;
            int idx = 0;
            while ((idx = string.indexOf(substring, idx)) != -1) {
                idx++;
                count++;
            }
            return count;
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            Log.d(LOG_TAG_DEBUG, "Method: ASYNC CalculateKeywordCountTask");
            String recordingText = mActivityRef.get().speechTextView.getText().toString();
            String keyword = mActivityRef.get().keyword;
            Log.d(LOG_TAG_DEBUG, "Method: CalculateKeywordCount");
            Log.d(LOG_TAG_DEBUG, "keyword: " + keyword);
            Log.d(LOG_TAG_DEBUG, "Final Result: " + recordingText);
            if (keyword != null) {
                return CountOfSubstringInString(recordingText, keyword);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result != null) {
                mActivityRef.get().keywordTextView.setText(String.valueOf(result));
            }
        }
    }

    private static class PrepareTextToSpeechTask extends AsyncTask<Void, Integer, Void> {

        WeakReference<MainActivity> activityRef;

        public PrepareTextToSpeechTask(MainActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d(LOG_TAG_DEBUG, "Method: ASYNC PrepareTextToSpeechTask");
            activityRef.get().textToSpeech = new TextToSpeech(activityRef.get(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    switch (status) {
                        case TextToSpeech.SUCCESS:
                            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activityRef.get());
                            String speechLang = preferences.getString("speakinglanguages", "en-US");
                            Log.d(LOG_TAG_DEBUG, "Speech Language: " + speechLang);
                            int result = activityRef.get().textToSpeech.setLanguage(Locale.forLanguageTag(speechLang));
                            if (result == TextToSpeech.LANG_MISSING_DATA
                                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                                Toast.makeText(getApplicationContext(), "This language is not supported", Toast.LENGTH_SHORT).show();
                                Log.w(LOG_TAG_DEBUG, "This language is not supported");
                            } else {
                                activityRef.get().readyToSpeak = true;
                                Log.d(LOG_TAG_DEBUG, "readyToSpeak:true");
                            }
                            break;
                        case TextToSpeech.ERROR:
                            Log.w(LOG_TAG_DEBUG, "TTS Error:" + status);
//                            Toast.makeText(getApplicationContext(), "TTS Initialization failed", Toast.LENGTH_SHORT).show();
                            activityRef.get().readyToSpeak = false;
                            break;
                        default:
                            Log.w(LOG_TAG_DEBUG, "Status of text to speech:" + status);
                            break;
                    }
                }
            });
            return null;
        }
    }
}
