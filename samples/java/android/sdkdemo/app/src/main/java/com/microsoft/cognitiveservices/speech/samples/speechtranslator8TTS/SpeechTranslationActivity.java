package com.microsoft.cognitiveservices.speech.samples.speechtranslator8TTS;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.microsoft.cognitiveservices.speech.AudioDataStream;
import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.translation.SpeechTranslationConfig;
import com.microsoft.cognitiveservices.speech.translation.TranslationRecognitionResult;
import com.microsoft.cognitiveservices.speech.translation.TranslationRecognizer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class SpeechTranslationActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    //
    // Configuration for speech recognition
    //
    // Replace below with your own subscription key
    private static String SpeechSubscriptionKey = "get from environment variable: SPEECH__SUBSCRIPTION__KEY";
    // Replace below with your own service region (e.g., "westus").
    private static String SpeechRegion = "get from environment variable: SPEECH__SERVICE__REGION";

    private ArrayList<String> supportedTranslators = new ArrayList<>();
    private HashMap<String, TranslateLocale> languageLocaleMap = new HashMap<>();
    private String fromLanguage;
    private String toLanguage;

    // STT
    private TranslationRecognizer translationRecognizer;
    private SpeechTranslationConfig translationConfig;
    private final AudioConfig audioConfig = AudioConfig.fromStreamInput(createMicrophoneStream());
    private MicrophoneStream microphoneStream;
    // TTS
    private SpeechSynthesizer speechSynthesizer;
    private SpeechConfig speechConfig;
    private SpeechSynthesisResult speechSynthesisResult;
    private final File myDirectory = new File(Environment.getExternalStorageDirectory(), "KK-sdkDemoSpeechTranslator");
    private Map<String, String> languageToVoiceMap;

    private EditText recognizedTextView;
    private EditText translatedTextView;
    private boolean isSaveToFile;
    private boolean isSpeakTranslatedText;

    private Button recognizeButton;
    private Button recognizeIntermediateButton;
    private Button recognizeContinuousButton;



    private MicrophoneStream createMicrophoneStream() {
        if (microphoneStream != null) {
            microphoneStream.close();
            microphoneStream = null;
        }

        microphoneStream = new MicrophoneStream();
        return microphoneStream;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(this.getClass().getName(), ">>>>>>>>> onCreate");
        setContentView(R.layout.activity_speech_translation);
        setTitle(R.string.speechTranslation);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        recognizedTextView = findViewById(R.id.recognizedText);
        translatedTextView = findViewById(R.id.translatedText);

        recognizeButton = findViewById(R.id.buttonRecognize);
        recognizeIntermediateButton = findViewById(R.id.buttonRecognizeIntermediate);
        recognizeContinuousButton = findViewById(R.id.buttonRecognizeContinuous);

        // Initialize SpeechSDK and request required permissions.
        try {
            // a unique number within the application to allow
            // correlating permission request responses with the request.
            int permissionRequestId = 5;

            // Request permissions needed for speech recognition
            ActivityCompat.requestPermissions(SpeechTranslationActivity.this, new String[]{RECORD_AUDIO, INTERNET, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE}, permissionRequestId);
        } catch (Exception ex) {
            Log.e("SpeechSDK", "could not init sdk, " + ex.toString());
            recognizedTextView.setText("Could not initialize: " + ex.toString());
        }

        // create config
        try {
            SpeechSubscriptionKey = BuildConfig.SPEECH__SUBSCRIPTION__KEY;
            SpeechRegion = BuildConfig.SPEECH__SERVICE__REGION;
            Log.i(this.getClass().getName(), "------------------- Environment variables -----------------------");
            Log.i(this.getClass().getName(), "SPEECH__SUBSCRIPTION__KEY: " + BuildConfig.SPEECH__SUBSCRIPTION__KEY);
            Log.i(this.getClass().getName(), "SPEECH__SERVICE__REGION: " + BuildConfig.SPEECH__SERVICE__REGION);
            translationConfig = SpeechTranslationConfig.fromSubscription(SpeechSubscriptionKey, SpeechRegion);
            speechConfig = SpeechConfig.fromSubscription(SpeechSubscriptionKey, SpeechRegion);

            // https://stackoverflow.com/questions/3013655/creating-hashmap-map-from-xml-resources
            String[] stringArray = getResources().getStringArray(R.array.translateLangLocaleMap);
            for (String entry : stringArray) {
                String[] splitResult = entry.split("\\|", 3);
                TranslateLocale translateLocale = new TranslateLocale(splitResult[1], splitResult[2]);
                languageLocaleMap.put(splitResult[0], translateLocale);
                supportedTranslators.add(splitResult[0]);
                translationConfig.addTargetLanguage(translateLocale.getToLanguage());
            }
            // https://www.tutlane.com/tutorial/android/android-spinner-dropdown-list-with-examples
            Spinner spin = (Spinner) findViewById(R.id.spinnerSelectLanguage);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, supportedTranslators);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spin.setAdapter(adapter);
            spin.setOnItemSelectedListener(this);

            // See: https://aka.ms/speech/sdkregion#standard-and-neural-voices
            // https://docs.microsoft.com/en-us/azure/cognitive-services/speech-service/language-support#neural-voices-in-preview
            languageToVoiceMap = new HashMap<String, String>();
            languageToVoiceMap.put("ko", "ko-KR-HeamiRUS");
            languageToVoiceMap.put("en", "en-US-BenjaminRUS");

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            displayException(ex);
            return;
        }


        ///////////////////////////////////////////////////
        // recognize
        ///////////////////////////////////////////////////
        recognizeButton.setOnClickListener(view -> {
            final String logTag = "reco 1";

            disableButtons();
            clearTextBox();

            try {
                translationConfig.setSpeechRecognitionLanguage(fromLanguage);
                translationRecognizer = new TranslationRecognizer(translationConfig, audioConfig);

                final Future<TranslationRecognitionResult> task = translationRecognizer.recognizeOnceAsync();
                setOnTaskCompletedListener(task, result -> {
                    String s = result.getText();
                    String translatedText = getTranslatedTextFromSTTResult(result);
                    if (result.getReason() != ResultReason.TranslatedSpeech) {
                        String errorDetails = (result.getReason() == ResultReason.Canceled) ? CancellationDetails.fromResult(result).getErrorDetails() : "";
                        s = "Recognition failed with " + result.getReason() + ". Did you enter your subscription?" + System.lineSeparator() + errorDetails;
                    }


                    Log.i(logTag, "Recognizer returned: " + s);
                    setRecognizedText(s);
                    setTranslatedText(translatedText);
                    enableButtons();
                    // process text to speech of translatedText
                    processTTS(translatedText);

                    translationRecognizer.close();
                    translationRecognizer = null;
                });
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                displayException(ex);
                enableButtons();
            }
        });

        ///////////////////////////////////////////////////
        // recognize with intermediate results
        ///////////////////////////////////////////////////
        recognizeIntermediateButton.setOnClickListener(view -> {
            final String logTag = "reco 2";

            disableButtons();
            clearTextBox();

            try {
                translationConfig.setSpeechRecognitionLanguage(fromLanguage);
                translationRecognizer = new TranslationRecognizer(translationConfig, audioConfig);

                translationRecognizer.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                    final String s = speechRecognitionResultEventArgs.getResult().getText();
                    Log.i(logTag, "Intermediate result received: " + s);
                    setRecognizedText(s);

                    String translatedText = getTranslatedTextFromSTTResult(speechRecognitionResultEventArgs.getResult());
                    setTranslatedText(translatedText);
                });

                final Future<TranslationRecognitionResult> task = translationRecognizer.recognizeOnceAsync();
                setOnTaskCompletedListener(task, result -> {
                    final String s = result.getText();
                    Log.i(logTag, "Recognizer returned: " + s);
                    setRecognizedText(s);

                    String translatedText = getTranslatedTextFromSTTResult(result);
                    setTranslatedText(translatedText);
                    enableButtons();

                    // process text to speech of translatedText
                    processTTS(translatedText);

                    translationRecognizer.close();
                    translationRecognizer = null;
                });
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                displayException(ex);
            }
        });

        ///////////////////////////////////////////////////
        // recognize continuously
        ///////////////////////////////////////////////////
        recognizeContinuousButton.setOnClickListener(new View.OnClickListener() {
            private static final String logTag = "reco 3";
            private boolean continuousListeningStarted = false;
            private String buttonText = "";
            private ArrayList<String> content = new ArrayList<>();
            private ArrayList<String> contentForTranslatedText = new ArrayList<>();
            private TranslationRecognizer reco = null;

            @Override
            public void onClick(final View view) {
                final Button clickedButton = (Button) view;
                disableButtons();
                if (continuousListeningStarted) {
                    if (reco != null) {
                        final Future<Void> task = reco.stopContinuousRecognitionAsync();
                        setOnTaskCompletedListener(task, result -> {
                            Log.i(logTag, "Continuous recognition stopped.");
                            SpeechTranslationActivity.this.runOnUiThread(() -> {
                                clickedButton.setText(buttonText);
                            });
                            enableButtons();
                            continuousListeningStarted = false;
                        });
                    } else {
                        continuousListeningStarted = false;
                    }

                    return;
                }

                clearTextBox();

                try {
                    content.clear();
                    contentForTranslatedText.clear();

                    translationConfig.setSpeechRecognitionLanguage(fromLanguage);
                    reco = new TranslationRecognizer(translationConfig, audioConfig);

                    reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s = speechRecognitionResultEventArgs.getResult().getText();
                        Log.i(logTag, "Intermediate result received: " + s);
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));
                        content.remove(content.size() - 1);

                        String translatedText = getTranslatedTextFromSTTResult(speechRecognitionResultEventArgs.getResult());
                        contentForTranslatedText.add(translatedText);
                        setTranslatedText(TextUtils.join(" ", contentForTranslatedText));
                        contentForTranslatedText.remove(contentForTranslatedText.size() - 1);
                    });

                    reco.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s = speechRecognitionResultEventArgs.getResult().getText();
                        Log.i(logTag, "Final result received: " + s);
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));

                        String translatedText = getTranslatedTextFromSTTResult(speechRecognitionResultEventArgs.getResult());
                        contentForTranslatedText.add(translatedText);
                        setTranslatedText(TextUtils.join(" ", contentForTranslatedText));

                        // process text to speech of translatedText
                        processTTS(translatedText);
                    });

                    final Future<Void> task = reco.startContinuousRecognitionAsync();
                    setOnTaskCompletedListener(task, result -> {
                        continuousListeningStarted = true;
                        SpeechTranslationActivity.this.runOnUiThread(() -> {
                            buttonText = clickedButton.getText().toString();
                            clickedButton.setText("Stop");
                            clickedButton.setEnabled(true);
                        });
                    });
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                    displayException(ex);
                }
            }
        });
    }

    private String getTranslatedTextFromSTTResult(TranslationRecognitionResult result) {
        String translatedText = "";
        for (Map.Entry<String, String> pair : result.getTranslations().entrySet()) {
            String language = pair.getKey();
            String translation = pair.getValue();
            System.out.printf("Translated into '%s': %s\n", language, translation);
            if (toLanguage.equals(language)) {
                translatedText = translation;
                break;
            }
        }
        return translatedText;
    }

    private void processTTS(String translatedText) {
        try {
            SpeechSynthesisResult ssResult = null;
            // speak text
            if(isSpeakTranslatedText){
                ssResult = speakText(translatedText);
                // show toast whether succeeded or failed
                toastResultOfTTS(ssResult);
            }
            // save
            if(isSaveToFile){
                saveToWavFile(ssResult);
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            displayException(ex);
        } finally {
            speechSynthesizer.close();
            speechSynthesizer = null;
            speechSynthesisResult.close();
            speechSynthesisResult = null;
        }
    }

    private void toastResultOfTTS(SpeechSynthesisResult ssResult) {
        String text = "";
        if (ssResult.getReason() == ResultReason.SynthesizingAudioCompleted) {
            text = "Speech synthesis succeeded.";
        } else if (ssResult.getReason() == ResultReason.Canceled) {
            String cancellationDetails =
                    SpeechSynthesisCancellationDetails.fromResult(ssResult).toString();
            text = "Error synthesizing. Error detail: " +
                    System.lineSeparator() + cancellationDetails +
                    System.lineSeparator() + "Did you update the subscription info?";
        }
        String finalText = text;
        SpeechTranslationActivity.this.runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(), finalText, Toast.LENGTH_SHORT).show();
        });
    }

    private void saveToWavFile(SpeechSynthesisResult ssResult) {
        AudioDataStream stream = AudioDataStream.fromResult(ssResult);
        System.out.printf("Steam status: " + stream.getStatus());
        // Create folder to store recordingss
        if (!myDirectory.exists()) {
            myDirectory.mkdirs();
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("mmddyyyyhhmmss");
        String date = dateFormat.format(new Date());
        String audioFile = toLanguage + "-translation-" + date + ".wav";
        String filePath = myDirectory.getAbsolutePath() + File.separator + audioFile;
        stream.saveToWavFile(filePath);
    }

    private SpeechSynthesisResult speakText(String translatedText) {
        speechConfig.setSpeechSynthesisVoiceName(languageToVoiceMap.get(toLanguage));
        //AudioConfig audioConfig = AudioConfig.fromWavFileOutput(language + "-translation.wav");
        // set the output format
        speechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Riff24Khz16BitMonoPcm);
        speechSynthesizer = new SpeechSynthesizer(speechConfig);
        speechSynthesisResult = speechSynthesizer.SpeakText(translatedText);
        return speechSynthesisResult;
    }

    private void displayException(Exception ex) {
        recognizedTextView.setText(ex.getMessage() + System.lineSeparator() + TextUtils.join(System.lineSeparator(), ex.getStackTrace()));
    }

    private void clearTextBox() {
        AppendTextLine("", true);
        AppendTextLineForTranslatedTex("", true);
    }

    private void setRecognizedText(final String s) {
        AppendTextLine(s, true);
    }

    private void AppendTextLine(final String s, final Boolean erase) {
        SpeechTranslationActivity.this.runOnUiThread(() -> {
            if (erase) {
                recognizedTextView.setText(s);
            } else {
                String txt = recognizedTextView.getText().toString();
                recognizedTextView.setText(txt + System.lineSeparator() + s);
            }
        });
    }

    private void setTranslatedText(final String s) {
        AppendTextLineForTranslatedTex(s, true);
    }

    private void AppendTextLineForTranslatedTex(final String s, final Boolean erase) {
        SpeechTranslationActivity.this.runOnUiThread(() -> {
            if (erase) {
                translatedTextView.setText(s);
            } else {
                String txt = translatedTextView.getText().toString();
                translatedTextView.setText(txt + System.lineSeparator() + s);
            }
        });
    }


    private void disableButtons() {
        SpeechTranslationActivity.this.runOnUiThread(() -> {
            recognizeButton.setEnabled(false);
            recognizeIntermediateButton.setEnabled(false);
            recognizeContinuousButton.setEnabled(false);
        });
    }

    private void enableButtons() {
        SpeechTranslationActivity.this.runOnUiThread(() -> {
            recognizeButton.setEnabled(true);
            recognizeIntermediateButton.setEnabled(true);
            recognizeContinuousButton.setEnabled(true);
        });
    }

    private <T> void setOnTaskCompletedListener(Future<T> task, SpeechTranslationActivity.OnTaskCompletedListener<T> listener) {
        s_executorService.submit(() -> {
            T result = task.get();
            listener.onCompleted(result);
            return null;
        });
    }

    private interface OnTaskCompletedListener<T> {
        void onCompleted(T taskResult);
    }

    private static ExecutorService s_executorService;

    static {
        s_executorService = Executors.newCachedThreadPool();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(this.getClass().getName(), ">>>>>>>>> onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(this.getClass().getName(), ">>>>>>>>> onStop");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(this.getClass().getName(), ">>>>>>>>> onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(this.getClass().getName(), ">>>>>>>>> onResume");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(this.getClass().getName(), ">>>>>>>>> onDestroy");
        // STT
        translationConfig.close();
        translationRecognizer.close();
        // TTS
        speechConfig.close();
        speechSynthesizer.close();
        speechSynthesisResult.close();
    }


    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
        // https://stackoverflow.com/questions/35449800/best-practice-to-implement-key-value-pair-in-android-spinner/35450251
        String language = supportedTranslators.get(position);
        TranslateLocale translateLocale = languageLocaleMap.get(language);
        Toast.makeText(getApplicationContext(), "Selected Language: " + language, Toast.LENGTH_SHORT).show();
        this.fromLanguage = translateLocale.getFromLanguage();
        this.toLanguage = translateLocale.getToLanguage();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    /**
     * Clear focus on touch outside for all EditText inputs.
     * https://gist.github.com/sc0rch/7c982999e5821e6338c25390f50d2993
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int)event.getRawX(), (int)event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }

            }
        }
        return super.dispatchTouchEvent( event );
    }

    public void onCheckboxClicked(View view) {
        // Is the view now checked?
        boolean checked = ((CheckBox) view).isChecked();
        // Check which checkbox was clicked
        switch(view.getId()) {
            case R.id.cbSaveToFile:
                isSaveToFile = checked;
                break;
            case R.id.cbSpeakTranslatedText:
                isSpeakTranslatedText = checked;
                break;
        }
    }

}