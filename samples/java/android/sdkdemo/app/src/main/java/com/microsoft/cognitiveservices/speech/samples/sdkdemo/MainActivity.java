//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
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

import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageConfig;
import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.KeywordRecognitionModel;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SourceLanguageConfig;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.intent.IntentRecognitionResult;
import com.microsoft.cognitiveservices.speech.intent.IntentRecognizer;
import com.microsoft.cognitiveservices.speech.intent.LanguageUnderstandingModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    //
    // Configuration for speech recognition
    //

    // Replace below with your own subscription key
    private static String SpeechSubscriptionKey = "get from environment variable: SPEECH__SUBSCRIPTION__KEY";
    // Replace below with your own service region (e.g., "westus").
    private static String SpeechRegion = "get from environment variable: SPEECH__SERVICE__REGION";

    private ArrayList<String> supportedLanguages = new ArrayList<>();
    private HashMap<String ,String> languageLocaleMap = new HashMap<>();
    private SpeechConfig speechConfig;
    private KeywordRecognitionModel kwsModel;
    private AutoDetectSourceLanguageConfig autoDetectSourceLanguageConfig;
    private SourceLanguageConfig sourceLanguageConfig;

    //
    // Configuration for intent recognition
    //

    // Replace below with your own Language Understanding subscription key
    // The intent recognition service calls the required key 'endpoint key'.
    private static final String LanguageUnderstandingSubscriptionKey = "YourLanguageUnderstandingSubscriptionKey";
    // Replace below with the deployment region of your Language Understanding application
    private static final String LanguageUnderstandingServiceRegion = "YourLanguageUnderstandingServiceRegion";
    // Replace below with the application ID of your Language Understanding application
    private static final String LanguageUnderstandingAppId = "YourLanguageUnderstandingAppId";
    // Replace below with your own Keyword model file, kws.table model file is configured for "Computer" keyword
    private static final String KwsModelFile = "kws.table";

    private TextView recognizedTextView;

    private Button recognizeButton;
    private Button recognizeIntermediateButton;
    private Button recognizeContinuousButton;
    private Button recognizeIntentButton;
    private Button recognizeWithKeywordButton;

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
        setContentView(R.layout.activity_main);

        recognizedTextView = findViewById(R.id.recognizedText);

        recognizeButton = findViewById(R.id.buttonRecognize);
        recognizeIntermediateButton = findViewById(R.id.buttonRecognizeIntermediate);
        recognizeContinuousButton = findViewById(R.id.buttonRecognizeContinuous);
        recognizeIntentButton = findViewById(R.id.buttonRecognizeIntent);
        recognizeWithKeywordButton = findViewById(R.id.buttonRecognizeWithKeyword);

        // Initialize SpeechSDK and request required permissions.
        try {
            // a unique number within the application to allow
            // correlating permission request responses with the request.
            int permissionRequestId = 5;

            // Request permissions needed for speech recognition
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{RECORD_AUDIO, INTERNET, READ_EXTERNAL_STORAGE}, permissionRequestId);
        } catch (Exception ex) {
            Log.e("SpeechSDK", "could not init sdk, " + ex.toString());
            recognizedTextView.setText("Could not initialize: " + ex.toString());
        }

        // create config
        try {
            // https://stackoverflow.com/questions/3013655/creating-hashmap-map-from-xml-resources
            String[] stringArray = getResources().getStringArray(R.array.languageLocaleMap);
            for (String entry : stringArray) {
                String[] splitResult = entry.split("\\|", 2);
                languageLocaleMap.put(splitResult[0], splitResult[1]);
                supportedLanguages.add(splitResult[0]);
            }
            // https://www.tutlane.com/tutorial/android/android-spinner-dropdown-list-with-examples
            Spinner spin = (Spinner) findViewById(R.id.spinnerSelectLanguage);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, supportedLanguages);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spin.setAdapter(adapter);
            spin.setOnItemSelectedListener(this);

            SpeechSubscriptionKey = BuildConfig.SPEECH__SUBSCRIPTION__KEY;
            SpeechRegion = BuildConfig.SPEECH__SERVICE__REGION;
            Log.i(this.getClass().getName(), "------------------- Environment variables -----------------------");
            Log.i(this.getClass().getName(), "SPEECH__SUBSCRIPTION__KEY: " + BuildConfig.SPEECH__SUBSCRIPTION__KEY);
            Log.i(this.getClass().getName(), "SPEECH__SERVICE__REGION: " + BuildConfig.SPEECH__SERVICE__REGION);
            autoDetectSourceLanguageConfig =
                    AutoDetectSourceLanguageConfig.fromLanguages(Arrays.asList("en-US", "ko-KR", "es-ES", "ru-RU"));
            speechConfig = SpeechConfig.fromSubscription(SpeechSubscriptionKey, SpeechRegion);
            kwsModel = KeywordRecognitionModel.fromFile(copyAssetToCacheAndGetFilePath(KwsModelFile));
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
                // final AudioConfig audioInput = AudioConfig.fromDefaultMicrophoneInput();
                final AudioConfig audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                //final SpeechRecognizer reco = new SpeechRecognizer(speechConfig, audioInput);
                //final SpeechRecognizer reco = new SpeechRecognizer(speechConfig, autoDetectSourceLanguageConfig, audioInput);
                final SpeechRecognizer reco = new SpeechRecognizer(speechConfig, sourceLanguageConfig, audioInput);

                final Future<SpeechRecognitionResult> task = reco.recognizeOnceAsync();
                setOnTaskCompletedListener(task, result -> {
                    String s = result.getText();
                    if (result.getReason() != ResultReason.RecognizedSpeech) {
                        String errorDetails = (result.getReason() == ResultReason.Canceled) ? CancellationDetails.fromResult(result).getErrorDetails() : "";
                        s = "Recognition failed with " + result.getReason() + ". Did you enter your subscription?" + System.lineSeparator() + errorDetails;
                    }

                    reco.close();
                    Log.i(logTag, "Recognizer returned: " + s);
                    setRecognizedText(s);
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
                // final AudioConfig audioInput = AudioConfig.fromDefaultMicrophoneInput();
                final AudioConfig audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                //final SpeechRecognizer reco = new SpeechRecognizer(speechConfig, audioInput);
                final SpeechRecognizer reco = new SpeechRecognizer(speechConfig, sourceLanguageConfig, audioInput);

                reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                    final String s = speechRecognitionResultEventArgs.getResult().getText();
                    Log.i(logTag, "Intermediate result received: " + s);
                    setRecognizedText(s);
                });

                final Future<SpeechRecognitionResult> task = reco.recognizeOnceAsync();
                setOnTaskCompletedListener(task, result -> {
                    final String s = result.getText();
                    reco.close();
                    Log.i(logTag, "Recognizer returned: " + s);
                    setRecognizedText(s);
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
            private SpeechRecognizer reco = null;
            private AudioConfig audioInput = null;
            private String buttonText = "";
            private ArrayList<String> content = new ArrayList<>();

            @Override
            public void onClick(final View view) {
                final Button clickedButton = (Button) view;
                disableButtons();
                if (continuousListeningStarted) {
                    if (reco != null) {
                        final Future<Void> task = reco.stopContinuousRecognitionAsync();
                        setOnTaskCompletedListener(task, result -> {
                            Log.i(logTag, "Continuous recognition stopped.");
                            MainActivity.this.runOnUiThread(() -> {
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

                    // audioInput = AudioConfig.fromDefaultMicrophoneInput();
                    audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                    //reco = new SpeechRecognizer(speechConfig, audioInput);
                    reco = new SpeechRecognizer(speechConfig, sourceLanguageConfig, audioInput);

                    reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s = speechRecognitionResultEventArgs.getResult().getText();
                        Log.i(logTag, "Intermediate result received: " + s);
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));
                        content.remove(content.size() - 1);
                    });

                    reco.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s = speechRecognitionResultEventArgs.getResult().getText();
                        Log.i(logTag, "Final result received: " + s);
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));
                    });

                    final Future<Void> task = reco.startContinuousRecognitionAsync();
                    setOnTaskCompletedListener(task, result -> {
                        continuousListeningStarted = true;
                        MainActivity.this.runOnUiThread(() -> {
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

        ///////////////////////////////////////////////////
        // recognize intent
        ///////////////////////////////////////////////////
        recognizeIntentButton.setOnClickListener(view -> {
            final String logTag = "intent";
            final ArrayList<String> content = new ArrayList<>();

            disableButtons();
            clearTextBox();

            content.add("");
            content.add("");
            try {
                final SpeechConfig intentConfig = SpeechConfig.fromSubscription(LanguageUnderstandingSubscriptionKey, LanguageUnderstandingServiceRegion);

                // final AudioConfig audioInput = AudioConfig.fromDefaultMicrophoneInput();
                final AudioConfig audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                final IntentRecognizer reco = new IntentRecognizer(intentConfig, audioInput);

                LanguageUnderstandingModel intentModel = LanguageUnderstandingModel.fromAppId(LanguageUnderstandingAppId);
                reco.addAllIntents(intentModel);

                reco.recognizing.addEventListener((o, intentRecognitionResultEventArgs) -> {
                    final String s = intentRecognitionResultEventArgs.getResult().getText();
                    Log.i(logTag, "Intermediate result received: " + s);
                    content.set(0, s);
                    setRecognizedText(TextUtils.join(System.lineSeparator(), content));
                });

                final Future<IntentRecognitionResult> task = reco.recognizeOnceAsync();
                setOnTaskCompletedListener(task, result -> {
                    Log.i(logTag, "Continuous recognition stopped.");
                    String s = result.getText();

                    if (result.getReason() != ResultReason.RecognizedIntent) {
                        String errorDetails = (result.getReason() == ResultReason.Canceled) ? CancellationDetails.fromResult(result).getErrorDetails() : "";
                        s = "Intent failed with " + result.getReason() + ". Did you enter your Language Understanding subscription?" + System.lineSeparator() + errorDetails;
                        CancellationDetails cancellation = CancellationDetails.fromResult(result);
                        System.out.println("CANCELED: Reason=" + cancellation.getReason());

                        if (cancellation.getReason() == CancellationReason.Error) {
                            System.out.println("CANCELED: ErrorCode=" + cancellation.getErrorCode());
                            System.out.println("CANCELED: ErrorDetails=" + cancellation.getErrorDetails());
                            System.out.println("CANCELED: Did you update the subscription info?");
                        }
                    }

                    String intentId = result.getIntentId();
                    Log.i(logTag, "Final result received: " + s + ", intent: " + intentId);
                    content.set(0, s);
                    content.set(1, " [intent: " + intentId + "]");
                    setRecognizedText(TextUtils.join(System.lineSeparator(), content));
                    enableButtons();
                });
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                displayException(ex);
            }
        });

        ///////////////////////////////////////////////////
        // recognize with keyword
        ///////////////////////////////////////////////////
        recognizeWithKeywordButton.setOnClickListener(new View.OnClickListener() {
            private static final String logTag = "keyword";
            private boolean continuousListeningStarted = false;
            private SpeechRecognizer reco = null;
            private AudioConfig audioInput = null;
            private String buttonText = "";
            private ArrayList<String> content = new ArrayList<>();

            @Override
            public void onClick(final View view) {
                final Button clickedButton = (Button) view;
                disableButtons();
                if (continuousListeningStarted) {
                    if (reco != null) {
                        final Future<Void> task = reco.stopKeywordRecognitionAsync();
                        setOnTaskCompletedListener(task, result -> {
                            Log.i(logTag, "Continuous recognition stopped.");
                            MainActivity.this.runOnUiThread(() -> {
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

                    // audioInput = AudioConfig.fromDefaultMicrophoneInput();
                    audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                    reco = new SpeechRecognizer(speechConfig, audioInput);

                    reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s = speechRecognitionResultEventArgs.getResult().getText();
                        Log.i(logTag, "Intermediate result received: " + s);
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));
                        content.remove(content.size() - 1);
                    });

                    reco.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String s;
                        if (speechRecognitionResultEventArgs.getResult().getReason() == ResultReason.RecognizedKeyword) {
                            s = "Keyword: " + speechRecognitionResultEventArgs.getResult().getText();
                            Log.i(logTag, "Keyword recognized result received: " + s);
                        } else {
                            s = "Recognized: " + speechRecognitionResultEventArgs.getResult().getText();
                            Log.i(logTag, "Final result received: " + s);
                        }
                        content.add(s);
                        setRecognizedText(TextUtils.join(" ", content));
                    });

                    final Future<Void> task = reco.startKeywordRecognitionAsync(kwsModel);
                    setOnTaskCompletedListener(task, result -> {
                        continuousListeningStarted = true;
                        MainActivity.this.runOnUiThread(() -> {
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
    }

    private void setRecognizedText(final String s) {
        AppendTextLine(s, true);
    }

    private void AppendTextLine(final String s, final Boolean erase) {
        MainActivity.this.runOnUiThread(() -> {
            if (erase) {
                recognizedTextView.setText(s);
            } else {
                String txt = recognizedTextView.getText().toString();
                recognizedTextView.setText(txt + System.lineSeparator() + s);
            }
        });
    }

    private void disableButtons() {
        MainActivity.this.runOnUiThread(() -> {
            recognizeButton.setEnabled(false);
            recognizeIntermediateButton.setEnabled(false);
            recognizeContinuousButton.setEnabled(false);
            recognizeIntentButton.setEnabled(false);
            recognizeWithKeywordButton.setEnabled(false);
        });
    }

    private void enableButtons() {
        MainActivity.this.runOnUiThread(() -> {
            recognizeButton.setEnabled(true);
            recognizeIntermediateButton.setEnabled(true);
            recognizeContinuousButton.setEnabled(true);
            recognizeIntentButton.setEnabled(true);
            recognizeWithKeywordButton.setEnabled(true);
        });
    }

    private <T> void setOnTaskCompletedListener(Future<T> task, OnTaskCompletedListener<T> listener) {
        s_executorService.submit(() -> {
            T result = task.get();
            listener.onCompleted(result);
            return null;
        });
    }

    private interface OnTaskCompletedListener<T> {
        void onCompleted(T taskResult);
    }

    private String copyAssetToCacheAndGetFilePath(String filename) {
        File cacheFile = new File(getCacheDir() + "/" + filename);
        if (!cacheFile.exists()) {
            try {
                InputStream is = getAssets().open(filename);
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                FileOutputStream fos = new FileOutputStream(cacheFile);
                fos.write(buffer);
                fos.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return cacheFile.getPath();
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
        this.speechConfig.close();
        this.sourceLanguageConfig.close();
        this.autoDetectSourceLanguageConfig.close();
    }


    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
        // https://stackoverflow.com/questions/35449800/best-practice-to-implement-key-value-pair-in-android-spinner/35450251
        String language = supportedLanguages.get(position);
        String locale = languageLocaleMap.get(language);
        Toast.makeText(getApplicationContext(), "Selected Language: "+ language + "\n Locale: " + locale,Toast.LENGTH_SHORT).show();
        this.sourceLanguageConfig = SourceLanguageConfig.fromLanguage(locale);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

}
