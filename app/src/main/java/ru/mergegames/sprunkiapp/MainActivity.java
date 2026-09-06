package ru.mergegames.sprunkiapp;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.caverock.androidsvg.SVG;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import ru.mergegames.sprunkiapp.databinding.ActivityMainBinding;

public class MainActivity extends Activity {

    // Константы
    private static final String KEY_CURRENT_PHASE = "current_phase";
    private static final String KEY_IS_LOADING = "is_loading";
    private static final String KEY_MENU_VISIBILITY = "menu_visibility";
    private static final String KEY_WEB_VISIBILITY = "web_visibility";
    
    private static final String COLOR_GRAY = "#808080";
    private static final String COLOR_WHITE = "#000000";
    
    private static final long LOADING_DELAY = 3000L;
    private static final long BUTTON_ANIMATION_DURATION = 750L;
    private static final long BUTTON_ANIMATION_DELAY = 150L;
    private static final long TOUCH_ANIMATION_DURATION = 150L;
    private static final float TOUCH_ALPHA_DOWN = 0.75f;
    private static final float TOUCH_ALPHA_UP = 1.0f;

    // UI
    private ActivityMainBinding binding;
    private WebView webView;
    
    // Состояние
    private String currentPhase = "";
    private boolean isLoadingPhase = false;
    
    // Кэш для SVG
    private final Map<String, Drawable> svgCache = new HashMap<>();
    
    // Звук
    private SoundPool soundPool;
    private int clickSoundId;
    
    // Handler с WeakReference
    private static class SafeHandler extends Handler {
        private final WeakReference<MainActivity> activityRef;
        
        SafeHandler(MainActivity activity) {
            activityRef = new WeakReference<>(activity);
        }
        
        @Override
        public void handleMessage(@NonNull android.os.Message msg) {
            MainActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                // Обработка сообщений
            }
        }
    }
    
    private final SafeHandler handler = new SafeHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initViews();
        initSound();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            showLoadingScreen();
            handler.postDelayed(() -> {
                MainActivity activity = this;
                if (!activity.isFinishing()) {
                    loadButtons();
                    showMenuScreen();
                }
            }, LOADING_DELAY);
        }
    }

    // ==================== ОБРАБОТКА КНОПКИ НАЗАД ====================
    
    @Override
    public void onBackPressed() {
        if (binding.webLayout.getVisibility() == View.VISIBLE) {
            // В WebView - показываем диалог возврата в меню
            showReturnDialog();
        } else if (binding.loadingLayout.getVisibility() == View.VISIBLE || 
                   binding.menuLayout.getVisibility() == View.VISIBLE) {
            // В загрузке или меню - показываем диалог выхода
            showExitDialog();
        }
        // В остальных случаях - ничего не делаем
    }

    private void initViews() {
        webView = binding.webView;
    }

    // ==================== ЗВУК ====================

    private void initSound() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build();

        clickSoundId = soundPool.load(this, R.raw.click, 1);
    }

    private void playClickSound() {
        if (soundPool != null && clickSoundId != 0) {
            soundPool.play(clickSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    // ==================== СОСТОЯНИЕ ====================
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CURRENT_PHASE, currentPhase);
        outState.putBoolean(KEY_IS_LOADING, isLoadingPhase);
        outState.putInt(KEY_MENU_VISIBILITY, binding.menuLayout.getVisibility());
        outState.putInt(KEY_WEB_VISIBILITY, binding.webLayout.getVisibility());
    }

    private void restoreState(@NonNull Bundle savedInstanceState) {
        currentPhase = savedInstanceState.getString(KEY_CURRENT_PHASE, "");
        isLoadingPhase = savedInstanceState.getBoolean(KEY_IS_LOADING, false);
        
        int menuVisibility = savedInstanceState.getInt(KEY_MENU_VISIBILITY);
        int webVisibility = savedInstanceState.getInt(KEY_WEB_VISIBILITY);
        
        binding.loadingLayout.setVisibility(View.GONE);
        binding.menuLayout.setVisibility(menuVisibility);
        binding.webLayout.setVisibility(webVisibility);
        
        if (webVisibility == View.VISIBLE && !currentPhase.isEmpty()) {
            setupWebView();
            webView.loadUrl("file:///android_asset/phase/" + currentPhase + ".html");
            showReturnButton();
        } else if (menuVisibility == View.VISIBLE) {
            loadButtons();
        }
    }

    // ==================== UI ЭКРАНЫ ====================

    private void showLoadingScreen() {
        binding.loadingLayout.setVisibility(View.VISIBLE);
        binding.menuLayout.setVisibility(View.GONE);
        binding.webLayout.setVisibility(View.GONE);
        setBackground(binding.loadingLayout, COLOR_GRAY);

        try {
            Glide.with(this)
                    .asGif()
                    .load(R.raw.loading)
                    .into(binding.loadingGif);
        } catch (Exception e) {
            e.printStackTrace();
            binding.loadingGif.setVisibility(View.GONE);
        }

        binding.loadingGif.setVisibility(View.VISIBLE);
        binding.loadingText.setVisibility(View.VISIBLE);
        binding.loadingText.setText(R.string.load);
        binding.loadingText.setTextColor(Color.WHITE);
    }

    private void showMenuScreen() {
        binding.loadingLayout.setVisibility(View.GONE);
        binding.menuLayout.setVisibility(View.VISIBLE);
        binding.webLayout.setVisibility(View.GONE);
        setBackground(binding.menuLayout, COLOR_GRAY);
        binding.menuTitle.setTextColor(Color.WHITE);
        animateButtonsAppearance();
    }

    private void setBackground(View view, String color) {
        view.setBackgroundColor(Color.parseColor(color));
    }

    // ==================== КНОПКИ МЕНЮ ====================

    private void loadButtons() {
        binding.buttonsContainer.removeAllViews();
        String[] buttonNames = {"1.svg", "2.svg", "4.svg"};

        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        int buttonSize = Math.min(screenWidth / 4, screenHeight / 4);
        int margin = buttonSize / 10;

        LinearLayout rowLayout = createRowLayout();
        
        for (String name : buttonNames) {
            ImageView button = createPhaseButton(name, buttonSize, margin);
            rowLayout.addView(button);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;
        binding.buttonsContainer.addView(rowLayout, params);
    }

    @NonNull
    private LinearLayout createRowLayout() {
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setGravity(Gravity.CENTER);
        return rowLayout;
    }

    @NonNull
    private ImageView createPhaseButton(String name, int buttonSize, int margin) {
        Drawable svgDrawable = loadSvgFromAssets("button/" + name);
        if (svgDrawable == null) {
            ImageView placeholder = new ImageView(this);
            placeholder.setBackgroundColor(Color.GRAY);
            return placeholder;
        }

        ImageView button = new ImageView(this);
        button.setImageDrawable(svgDrawable);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        params.setMargins(margin, 0, margin, 0);
        params.gravity = Gravity.CENTER;
        button.setLayoutParams(params);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setAdjustViewBounds(true);

        setupButtonTouchAnimation(button);
        
        button.setOnClickListener(v -> {
            if (!isLoadingPhase) {
                playClickSound();
                String phaseNumber = name.replace(".svg", "");
                openPhase(phaseNumber);
            }
        });

        return button;
    }

    // ==================== SVG ЗАГРУЗКА С КЭШЕМ ====================

    @Nullable
    private Drawable loadSvgFromAssets(String path) {
        if (svgCache.containsKey(path)) {
            return svgCache.get(path);
        }
        
        try {
            InputStream is = getAssets().open(path);
            SVG svg = SVG.getFromInputStream(is);
            Picture picture = svg.renderToPicture();
            Drawable drawable = new PictureDrawable(picture);
            svgCache.put(path, drawable);
            return drawable;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== АНИМАЦИИ ====================

    private void setupButtonTouchAnimation(View button) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().alpha(TOUCH_ALPHA_DOWN)
                            .setDuration(TOUCH_ANIMATION_DURATION).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().alpha(TOUCH_ALPHA_UP)
                            .setDuration(TOUCH_ANIMATION_DURATION).start();
                    break;
            }
            return false;
        });
    }

    private void animateButtonsAppearance() {
        if (binding.buttonsContainer.getChildCount() == 0) return;
        
        View firstChild = binding.buttonsContainer.getChildAt(0);
        if (!(firstChild instanceof ViewGroup)) return;
        
        ViewGroup rowLayout = (ViewGroup) firstChild;
        for (int i = 0; i < rowLayout.getChildCount(); i++) {
            View child = rowLayout.getChildAt(i);
            child.setScaleX(0f);
            child.setScaleY(0f);

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(BUTTON_ANIMATION_DURATION);
            animator.setStartDelay(i * BUTTON_ANIMATION_DELAY);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                child.setScaleX(value);
                child.setScaleY(value);
            });
            animator.start();
        }
    }

    // ==================== РАБОТА С ФАЗАМИ ====================

    private void openPhase(String phaseNumber) {
        isLoadingPhase = true;
        currentPhase = phaseNumber;

        binding.menuLayout.setVisibility(View.GONE);
        binding.webLayout.setVisibility(View.VISIBLE);
        setBackground(binding.webLayout, COLOR_WHITE);

        setupWebView();

        try {
            String htmlPath = "file:///android_asset/phase/" + phaseNumber + ".html";
            webView.clearCache(true);
            webView.loadUrl(htmlPath);
        } catch (Exception e) {
            e.printStackTrace();
            isLoadingPhase = false;
            showErrorDialog();
        }

        showReturnButton();
    }

    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(
                    android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            );
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                isLoadingPhase = false;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, 
                                       String description, String failingUrl) {
                isLoadingPhase = false;
                showErrorDialog();
            }
        });

        webView.setBackgroundColor(Color.WHITE);
        webView.clearHistory();
        webView.clearFormData();
    }

    // ==================== КНОПКА ВОЗВРАТА ====================
    
    private void showReturnButton() {
        Drawable svgDrawable = loadSvgFromAssets("button/return.svg");
        if (svgDrawable == null) return;

        binding.returnButton.setImageDrawable(svgDrawable);
        binding.returnButton.setVisibility(View.VISIBLE);

        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int size = screenWidth / 12;

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.setMargins(10, 10, 0, 0);
        params.gravity = Gravity.START | Gravity.TOP;
        binding.returnButton.setLayoutParams(params);
        binding.returnButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        binding.returnButton.setAdjustViewBounds(true);

        setupButtonTouchAnimation(binding.returnButton);

        binding.returnButton.setOnClickListener(v -> {
            if (!isLoadingPhase) {
                playClickSound();
                showReturnDialog();
            }
        });
    }

    // ==================== ДИАЛОГИ ====================

    private void showErrorDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.error_phase_load))
                .setPositiveButton("OK", (dialogInterface, which) -> {
                    playClickSound();
                    binding.webLayout.setVisibility(View.GONE);
                    binding.menuLayout.setVisibility(View.VISIBLE);
                    binding.returnButton.setVisibility(View.GONE);
                    setBackground(binding.menuLayout, COLOR_GRAY);
                    loadButtons();
                    animateButtonsAppearance();
                })
                .setCancelable(false)
                .create();
        
        dialog.setOnShowListener(dialogInterface -> {
            AlertDialog alertDialog = (AlertDialog) dialogInterface;
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                playClickSound();
                alertDialog.dismiss();
                binding.webLayout.setVisibility(View.GONE);
                binding.menuLayout.setVisibility(View.VISIBLE);
                binding.returnButton.setVisibility(View.GONE);
                setBackground(binding.menuLayout, COLOR_GRAY);
                loadButtons();
                animateButtonsAppearance();
            });
        });
        
        dialog.show();
    }

    private void showReturnDialog() {
        String message = String.format(getString(R.string.return_message), currentPhase);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("✔", (dialogInterface, which) -> {
                    playClickSound();
                    binding.webLayout.setVisibility(View.GONE);
                    binding.menuLayout.setVisibility(View.VISIBLE);
                    binding.returnButton.setVisibility(View.GONE);
                    setBackground(binding.menuLayout, COLOR_GRAY);
                    webView.loadUrl("about:blank");
                    webView.clearCache(true);
                    loadButtons();
                    animateButtonsAppearance();
                })
                .setNegativeButton("✘", (dialogInterface, which) -> {
                    playClickSound();
                    dialogInterface.dismiss();
                })
                .setCancelable(false)
                .create();
        
        dialog.setOnShowListener(dialogInterface -> {
            AlertDialog alertDialog = (AlertDialog) dialogInterface;
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                playClickSound();
                alertDialog.dismiss();
                binding.webLayout.setVisibility(View.GONE);
                binding.menuLayout.setVisibility(View.VISIBLE);
                binding.returnButton.setVisibility(View.GONE);
                setBackground(binding.menuLayout, COLOR_GRAY);
                webView.loadUrl("about:blank");
                webView.clearCache(true);
                loadButtons();
                animateButtonsAppearance();
            });
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                playClickSound();
                alertDialog.dismiss();
            });
        });
        
        dialog.show();
    }

    private void showExitDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.exit_game))
                .setPositiveButton("✔", (dialogInterface, which) -> {
                    playClickSound();
                    finish();
                })
                .setNegativeButton("✘", (dialogInterface, which) -> {
                    playClickSound();
                    dialogInterface.dismiss();
                })
                .setCancelable(false)
                .create();
        
        dialog.setOnShowListener(dialogInterface -> {
            AlertDialog alertDialog = (AlertDialog) dialogInterface;
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                playClickSound();
                alertDialog.dismiss();
                finish();
            });
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                playClickSound();
                alertDialog.dismiss();
            });
        });
        
        dialog.show();
    }

    // ==================== СИСТЕМНЫЕ МЕТОДЫ ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        svgCache.clear();
        
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        
        binding = null;
        
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearCache(true);
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
    }
}