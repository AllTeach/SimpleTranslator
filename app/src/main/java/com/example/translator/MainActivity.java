package com.example.translator;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_RECORD_PERMISSION = 1001;
    private static final String PREFS = "app_prefs";
    private static final String KEY_AUTOPLAY = "auto_play";

    private TextView tvStatus, tvRecognized, tvTranslated;
    private MaterialButton btnSpeakEl, btnSpeakEn;
    private ProgressBar progressBar;
    private SwitchMaterial switchAutoPlay;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private LanguageIdentifier languageIdentifier;

    // conversation state used to carry source/target between start and onResults
    private String currentConversationSourceIso = "el";
    private String currentConversationTargetIso = "en";

    // runtime flag persisted in SharedPreferences (default ON)
    private boolean autoPlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // views
        tvStatus = findViewById(R.id.tvStatus);
        tvRecognized = findViewById(R.id.tvRecognized);
        tvTranslated = findViewById(R.id.tvTranslated);
        progressBar = findViewById(R.id.progressBar);
        switchAutoPlay = findViewById(R.id.switchAutoPlay);
        btnSpeakEl = findViewById(R.id.btnSpeakEl);
        btnSpeakEn = findViewById(R.id.btnSpeakEn);

        // load persisted preference (default true)
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        autoPlay = prefs.getBoolean(KEY_AUTOPLAY, true);
        switchAutoPlay.setChecked(autoPlay);
        switchAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            autoPlay = isChecked;
            prefs.edit().putBoolean(KEY_AUTOPLAY, autoPlay).apply();
        });

        // TTS init
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
            }
        });

        // LanguageIdentifier (kept for future fallback)
        languageIdentifier = LanguageIdentification.getClient();

        // Wire speak buttons
        btnSpeakEl.setOnClickListener(v -> startConversationListen("el-GR", "el", "en"));
        btnSpeakEn.setOnClickListener(v -> startConversationListen("en-US", "en", "el"));

        // SpeechRecognizer setup (single instance reused)
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
                    showProgress(false);
                    enableSpeakButtons(true);
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognized = matches.get(0);
                        tvRecognized.setText(recognized);
                        // translate-from currentConversationSourceIso -> currentConversationTargetIso
                        translateAndMaybeSpeak(recognized, currentConversationSourceIso, currentConversationTargetIso);
                    } else {
                        tvStatus.setText("No speech recognized");
                        showProgress(false);
                        enableSpeakButtons(true);
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } else {
            btnSpeakEl.setEnabled(false);
            btnSpeakEn.setEnabled(false);
            tvStatus.setText("Speech recognition not available on this device");
        }
    }

    // Start listening for a conversation turn (ASR forced to asrBcp47)
    private void startConversationListen(String asrBcp47, String sourceIso, String targetIso) {
        // permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_PERMISSION);
            return;
        }

        // set conversation direction state
        currentConversationSourceIso = sourceIso;
        currentConversationTargetIso = targetIso;

        // UI
        tvStatus.setText("Ready to speak (" + sourceIso + ")...");
        tvRecognized.setText("");
        tvTranslated.setText("");
        showProgress(true);
        enableSpeakButtons(false);

        // prepare ASR intent forced to the chosen language
        android.content.Intent intent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, asrBcp47);

        if (speechRecognizer != null) {
            speechRecognizer.startListening(intent);
        }
    }

    // Translate and optionally TTS the translated text (re-enable UI at the end)
    private void translateAndMaybeSpeak(String text, String sourceIso, String targetIso) {
        tvStatus.setText("Translating...");
        showProgress(true);

        // normalize codes to 2-letter for Translate API
        String src = (sourceIso != null && sourceIso.length() >= 2) ? sourceIso.substring(0,2) : "el";
        String tgt = (targetIso != null && targetIso.length() >= 2) ? targetIso.substring(0,2) : "en";

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(tgt)
                .build();
        final Translator translator = Translation.getClient(options);

        // allow any network; change to requireWifi() if needed
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(aVoid -> translator.translate(text)
                        .addOnSuccessListener(translation -> {
                            tvTranslated.setText(translation);
                            tvStatus.setText("Done");
                            boolean ttsReady = setTtsLocaleForLanguage(tgt);
                            if (autoPlay) {
                                if (ttsReady && tts != null) {
                                    tts.speak(translation, TextToSpeech.QUEUE_FLUSH, null, "conv_utt");
                                } else {
                                    Toast.makeText(this, "TTS language not available for playback", Toast.LENGTH_SHORT).show();
                                }
                            }
                            showProgress(false);
                            enableSpeakButtons(true);
                            try { translator.close(); } catch (Exception ignored) {}
                        })
                        .addOnFailureListener(e -> {
                            tvStatus.setText("Translation failed: " + e.getMessage());
                            showProgress(false);
                            enableSpeakButtons(true);
                            try { translator.close(); } catch (Exception ignored) {}
                        })
                )
                .addOnFailureListener(e -> {
                    tvStatus.setText("Model download failed: " + e.getMessage());
                    showProgress(false);
                    enableSpeakButtons(true);
                    try { translator.close(); } catch (Exception ignored) {}
                });
    }

    // Convenience: enable/disable speak buttons
    private void enableSpeakButtons(boolean enable) {
        btnSpeakEl.setEnabled(enable);
        btnSpeakEn.setEnabled(enable);
    }

    private void showProgress(boolean show) {
        if (progressBar == null) return;
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // returns true if TTS language is available/supported
    private boolean setTtsLocaleForLanguage(String langCode) {
        if (tts == null) return false;
        Locale locale;
        if (langCode != null && langCode.startsWith("el")) {
            locale = new Locale("el", "GR");
        } else if (langCode != null && langCode.startsWith("en")) {
            locale = Locale.ENGLISH;
        } else {
            locale = Locale.getDefault();
        }
        int res = tts.setLanguage(locale);
        return !(res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (languageIdentifier != null) {
            languageIdentifier.close();
            languageIdentifier = null;
        }
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
    }

    // Permissions callback
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQ_RECORD_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted — press your language button to speak", Toast.LENGTH_SHORT).show();
            } else {
                tvStatus.setText("Microphone permission required");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}