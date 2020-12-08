package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.translation.SpeechTranslationConfig;
import com.microsoft.cognitiveservices.speech.translation.TranslationRecognitionResult;
import com.microsoft.cognitiveservices.speech.translation.TranslationRecognizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;

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
    private SpeechTranslationConfig translationConfig;

    private TextView recognizedTextView;
    private TextView translatedTextView;

    private Button recognizeButton;
    private Button recognizeIntermediateButton;
    private Button recognizeContinuousButton;

    private MicrophoneStream microphoneStream;


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
            ActivityCompat.requestPermissions(SpeechTranslationActivity.this, new String[]{RECORD_AUDIO, INTERNET, READ_EXTERNAL_STORAGE}, permissionRequestId);
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
                final AudioConfig audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                translationConfig.setSpeechRecognitionLanguage(fromLanguage);

                final TranslationRecognizer reco = new TranslationRecognizer(translationConfig, audioInput);

                final Future<TranslationRecognitionResult> task = reco.recognizeOnceAsync();
                setOnTaskCompletedListener(task, result -> {
                    String s = result.getText();
                    String translatedText = "";
                    for (Map.Entry<String, String> pair : result.getTranslations().entrySet()) {
                        System.out.printf("Translated into '%s': %s\n", pair.getKey(), pair.getValue());
                        if(toLanguage.contains(pair.getKey())){
                            translatedText = pair.getValue();
                        }
                    }
                    if (result.getReason() != ResultReason.TranslatedSpeech) {
                        String errorDetails = (result.getReason() == ResultReason.Canceled) ? CancellationDetails.fromResult(result).getErrorDetails() : "";
                        s = "Recognition failed with " + result.getReason() + ". Did you enter your subscription?" + System.lineSeparator() + errorDetails;
                    }

                    reco.close();
                    Log.i(logTag, "Recognizer returned: " + s);
                    setRecognizedText(s);
                    setTranslatedText(translatedText);
                    enableButtons();
                });
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                displayException(ex);
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
                final AudioConfig audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                translationConfig.setSpeechRecognitionLanguage(fromLanguage);
                final TranslationRecognizer reco = new TranslationRecognizer(translationConfig, audioInput);

                reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                    final String s = speechRecognitionResultEventArgs.getResult().getText();
                    Log.i(logTag, "Intermediate result received: " + s);
                    setRecognizedText(s);

                    String translatedText = "";
                    for (Map.Entry<String, String> pair : speechRecognitionResultEventArgs.getResult().getTranslations().entrySet()) {
                        System.out.printf("Translated into '%s': %s\n", pair.getKey(), pair.getValue());
                        if(toLanguage.contains(pair.getKey())){
                            translatedText = pair.getValue();
                        }
                    }
                    setTranslatedText(translatedText);
                });

                final Future<TranslationRecognitionResult> task = reco.recognizeOnceAsync();
                setOnTaskCompletedListener(task, result -> {
                    final String s = result.getText();
                    reco.close();
                    Log.i(logTag, "Recognizer returned: " + s);
                    setRecognizedText(s);

                    String translatedText = "";
                    for (Map.Entry<String, String> pair : result.getTranslations().entrySet()) {
                        System.out.printf("Translated into '%s': %s\n", pair.getKey(), pair.getValue());
                        if(toLanguage.contains(pair.getKey())){
                            translatedText = pair.getValue();
                        }
                    }
                    setTranslatedText(translatedText);

                    enableButtons();
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
            private TranslationRecognizer reco = null;
            private AudioConfig audioInput = null;
            private String buttonText = "";
            private ArrayList<String> content = new ArrayList<>();
            private ArrayList<String> contentForTranslatedText = new ArrayList<>();

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

                    audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                    translationConfig.setSpeechRecognitionLanguage(fromLanguage);
                    reco = new TranslationRecognizer(translationConfig, audioInput);

                    reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s = speechRecognitionResultEventArgs.getResult().getText();
                        Log.i(logTag, "Intermediate result received: " + s);
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));
                        content.remove(content.size() - 1);

                        String translatedText = "";
                        for (Map.Entry<String, String> pair : speechRecognitionResultEventArgs.getResult().getTranslations().entrySet()) {
                            System.out.printf("Translated into '%s': %s\n", pair.getKey(), pair.getValue());
                            if(toLanguage.contains(pair.getKey())){
                                translatedText = pair.getValue();
                            }
                        }
                        contentForTranslatedText.add(translatedText);
                        setTranslatedText(TextUtils.join(" ", contentForTranslatedText));
                        contentForTranslatedText.remove(contentForTranslatedText.size() - 1);
                    });

                    reco.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s = speechRecognitionResultEventArgs.getResult().getText();
                        Log.i(logTag, "Final result received: " + s);
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));

                        String translatedText = "";
                        for (Map.Entry<String, String> pair : speechRecognitionResultEventArgs.getResult().getTranslations().entrySet()) {
                            System.out.printf("Translated into '%s': %s\n", pair.getKey(), pair.getValue());
                            if(toLanguage.contains(pair.getKey())){
                                translatedText = pair.getValue();
                            }
                        }
                        contentForTranslatedText.add(translatedText);
                        setTranslatedText(TextUtils.join(" ", contentForTranslatedText));
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
        this.translationConfig.close();
    }


    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
        // https://stackoverflow.com/questions/35449800/best-practice-to-implement-key-value-pair-in-android-spinner/35450251
        String language = supportedTranslators.get(position);
        TranslateLocale translateLocale = languageLocaleMap.get(language);
        Toast.makeText(getApplicationContext(), "Selected Language: "+ language,Toast.LENGTH_SHORT).show();
        this.fromLanguage = translateLocale.getFromLanguage();
        this.toLanguage = translateLocale.getToLanguage();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

}