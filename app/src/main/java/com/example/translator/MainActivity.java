package com.example.translator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_RECORD_PERMISSION = 1001;

    private TextView tvStatus, tvRecognized, tvTranslated;
    private Button btnListen, btnSpeakTranslation, btnManageModels;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvRecognized = findViewById(R.id.tvRecognized);
        tvTranslated = findViewById(R.id.tvTranslated);
        btnListen = findViewById(R.id.btnListen);
        btnSpeakTranslation = findViewById(R.id.btnSpeakTranslation);
        btnManageModels = findViewById(R.id.btnManageModels);

        // init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
            }
        });

        btnListen.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_RECORD_PERMISSION);
            } else {
                startListening();
            }
        });

        btnManageModels.setOnClickListener(v -> {
            startActivity(new Intent(this, DownloadModelsActivity.class));
        });

        btnSpeakTranslation.setOnClickListener(v -> {
            String txt = tvTranslated.getText().toString();
            if (!txt.isEmpty() && !txt.equals("—")) {
                tts.speak(txt, TextToSpeech.QUEUE_FLUSH, null, "translationId");
            }
        });

        // init SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { tvStatus.setText("Listening..."); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { tvStatus.setText("Processing..."); }
                @Override public void onError(int error) {
                    tvStatus.setText("Recognition error: " + error);
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognized = matches.get(0);
                        tvRecognized.setText(recognized);
                        detectLanguageAndTranslate(recognized);
                    } else {
                        tvStatus.setText("No speech recognized");
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } else {
            btnListen.setEnabled(false);
            tvStatus.setText("Speech recognition not available on this device");
        }
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.startListening(intent);
        tvStatus.setText("Ready to speak");
    }

    private void detectLanguageAndTranslate(String text) {
        tvStatus.setText("Detecting language...");
        LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener(lang -> {
                if (lang.equals("und")) {
                    tvStatus.setText("Could not identify language");
                    return;
                }
                tvStatus.setText("Detected: " + lang);
                String sourceLang = lang;
                String targetLang;

                if (sourceLang.startsWith("en")) {
                    targetLang = TranslateLanguage.GREEK; // "el"
                } else if (sourceLang.startsWith("el")) {
                    targetLang = TranslateLanguage.ENGLISH; // "en"
                } else {
                    targetLang = TranslateLanguage.ENGLISH;
                }

                translateText(text, sourceLang, targetLang);
            })
            .addOnFailureListener(e -> {
                tvStatus.setText("Language detection failed: " + e.getMessage());
                translateText(text, "auto", TranslateLanguage.ENGLISH);
            });
    }

    private void translateText(String text, String sourceLang, String targetLang) {
        tvStatus.setText("Translating to " + targetLang + "...");
        String src = sourceLang;
        if (sourceLang.equals("auto")) src = TranslateLanguage.ENGLISH;
        if (src.length() > 2) src = src.substring(0,2);

        TranslatorOptions options = new TranslatorOptions.Builder()
            .setSourceLanguage(src)
            .setTargetLanguage(targetLang)
            .build();

        final Translator translator = com.google.mlkit.nl.translate.Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
            .requireWifi()
            .build();

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener(aVoid -> {
                translator.translate(text)
                    .addOnSuccessListener(translation -> {
                        tvTranslated.setText(translation);
                        tvStatus.setText("Done");
                        setTtsLocaleForLanguage(targetLang);
                        translator.close();
                    })
                    .addOnFailureListener(e -> {
                        tvStatus.setText("Translation failed: " + e.getMessage());
                        translator.close();
                    });
            })
            .addOnFailureListener(e -> {
                tvStatus.setText("Model download failed: " + e.getMessage());
                translator.close();
            });
    }

    private void setTtsLocaleForLanguage(String langCode) {
        Locale locale;
        if (langCode.equalsIgnoreCase(TranslateLanguage.GREEK) || langCode.startsWith("el")) {
            locale = new Locale("el");
        } else if (langCode.equalsIgnoreCase(TranslateLanguage.ENGLISH) || langCode.startsWith("en")) {
            locale = Locale.ENGLISH;
        } else {
            locale = Locale.getDefault();
        }
        tts.setLanguage(locale);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) tts.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
        @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQ_RECORD_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                tvStatus.setText("Microphone permission required");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
