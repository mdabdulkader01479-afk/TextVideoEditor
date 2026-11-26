package com.texteditor.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public class MainActivity extends Activity {

    private static final int REQ_PICK_VIDEO = 101;
    private float d;
    private int p8;

    private VideoView videoView;
    private CanvasText cText;
    private EditText edBox;
    private SeekBar timelineSeek;
    private TextView txtTime;
    private Button btnPlay;
    private Uri selectedVideoUri;
    private int videoDurationMs = 10000;
    private boolean isPlaying = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static class HlSpan {
        int c;
        HlSpan(int c) { this.c = c; }
    }

    static class TfSpan extends MetricAffectingSpan {
        Typeface tf;
        TfSpan(Typeface t) { this.tf = t; }
        @Override public void updateDrawState(TextPaint ds) { ds.setTypeface(tf); }
        @Override public void updateMeasureState(TextPaint p) { p.setTypeface(tf); }
    }

    class CanvasText extends TextView {
        Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF hr = new RectF();
        float sw = 0f;
        int sc = Color.BLACK;
        float sf = 1f, dx = 0f, dy = 0f;
        boolean scaling = false;
        ScaleGestureDetector sgd;

        CanvasText(Context c) {
            super(c);
            setTextColor(Color.WHITE);
            setTextSize(22);
            setGravity(Gravity.CENTER);
            setPadding((int)(14 * d), (int)(10 * d), (int)(14 * d), (int)(10 * d));
            sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScaleBegin(ScaleGestureDetector det) { scaling = true; return true; }
                @Override public boolean onScale(ScaleGestureDetector det) {
                    sf = Math.max(0.4f, Math.min(sf * det.getScaleFactor(), 3.5f));
                    setScaleX(sf);
                    setScaleY(sf);
                    return true;
                }
                @Override public void onScaleEnd(ScaleGestureDetector det) { scaling = false; }
            });
        }

        @Override protected void onDraw(Canvas canvas) {
            Layout l = getLayout();
            CharSequence cs = getText();
            if (l != null && cs instanceof Spanned) {
                Spanned t = (Spanned) cs;
                HlSpan[] spans = t.getSpans(0, t.length(), HlSpan.class);
                int pl = getTotalPaddingLeft(), pt = getTotalPaddingTop();
                for (HlSpan sp : spans) {
                    int s = t.getSpanStart(sp), e = t.getSpanEnd(sp);
                    if (s < 0 || e <= s) continue;
                    hp.setColor(sp.c);
                    int sl = l.getLineForOffset(s), el = l.getLineForOffset(e);
                    for (int i = sl; i <= el; i++) {
                        float lf = (i == sl) ? l.getPrimaryHorizontal(s) : l.getLineLeft(i);
                        float rt = (i == el) ? l.getPrimaryHorizontal(e) : l.getLineRight(i);
                        if (lf > rt) { float tmp = lf; lf = rt; rt = tmp; }
                        hr.set(pl + lf - 5 * d, pt + l.getLineTop(i) + 2 * d, pl + rt + 5 * d, pt + l.getLineBottom(i) - 2 * d);
                        canvas.drawRoundRect(hr, 6 * d, 6 * d, hp);
                    }
                }
            }
            if (sw > 0) {
                ColorStateList oc = getTextColors();
                getPaint().setStyle(Paint.Style.STROKE);
                getPaint().setStrokeWidth(sw);
                setTextColor(sc);
                super.onDraw(canvas);
                getPaint().setStyle(Paint.Style.FILL);
                setTextColor(oc);
            }
            super.onDraw(canvas);
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            sgd.onTouchEvent(ev);
            if (ev.getPointerCount() == 1 && !scaling) {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dx = getX() - ev.getRawX();
                    dy = getY() - ev.getRawY();
                } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    setX(ev.getRawX() + dx);
                    setY(ev.getRawY() + dy);
                }
            }
            return true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        p8 = (int)(8 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));

        // --- TOP BAR ---
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#1E1E1E"));
        topBar.setPadding(p8, p8, p8, p8);
        root.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        final TextView txtRatio = new TextView(this);
        txtRatio.setText("9:16");
        txtRatio.setTextColor(Color.WHITE);
        txtRatio.setTextSize(14);
        txtRatio.setTypeface(null, Typeface.BOLD);
        txtRatio.setPadding(p8 * 2, p8, p8 * 2, p8);
        txtRatio.setBackgroundColor(Color.parseColor("#333333"));
        topBar.addView(txtRatio);

        topBar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        Button btnExport = new Button(this);
        btnExport.setText("EXPORT");
        btnExport.setTextColor(Color.WHITE);
        btnExport.setBackgroundColor(Color.parseColor("#007AFF"));
        btnExport.setTextSize(13);
        btnExport.setOnClickListener(v -> Toast.makeText(this, "MP4 Export coming in next update!", Toast.LENGTH_SHORT).show());
        topBar.addView(btnExport, new LinearLayout.LayoutParams(-2, (int)(36 * d)));

        // --- PREVIEW AREA ---
        final FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(Color.parseColor("#0A0A0A"));
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1f));

        final FrameLayout canvasBox = new FrameLayout(this);
        canvasBox.setBackgroundColor(Color.parseColor("#1A1A1A"));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams((int)(220 * d), (int)(390 * d));
        cp.gravity = Gravity.CENTER;
        preview.addView(canvasBox, cp);

        videoView = new VideoView(this);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(-1, -1);
        vp.gravity = Gravity.CENTER;
        canvasBox.addView(videoView, vp);

        SpannableStringBuilder doc = new SpannableStringBuilder("Tap Video to load a clip. Tap Text to highlight words.");
        cText = new CanvasText(this);
        cText.setText(doc, TextView.BufferType.SPANNABLE);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2);
        tp.gravity = Gravity.CENTER;
        canvasBox.addView(cText, tp);

        // Ratio switcher
        final String[] ratios = {"9:16", "16:9", "1:1", "4:3"};
        final int[] rIdx = {0};
        txtRatio.setOnClickListener(v -> {
            rIdx[0] = (rIdx[0] + 1) % 4;
            String r = ratios[rIdx[0]];
            txtRatio.setText(r);
            int mw = preview.getWidth() - (int)(20 * d), mh = preview.getHeight() - (int)(20 * d);
            if (mw <= 0 || mh <= 0) return;
            float tr = r.equals("16:9") ? 16f / 9f : (r.equals("1:1") ? 1f : (r.equals("4:3") ? 4f / 3f : 9f / 16f));
            int nw = mw, nh = (int)(nw / tr);
            if (nh > mh) { nh = mh; nw = (int)(nh * tr); }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(nw, nh);
            lp.gravity = Gravity.CENTER;
            canvasBox.setLayoutParams(lp);
        });

        // --- TEXT EDITOR PANEL ---
        final LinearLayout textPanel = new LinearLayout(this);
        textPanel.setOrientation(LinearLayout.VERTICAL);
        textPanel.setBackgroundColor(Color.parseColor("#1C1C1E"));
        textPanel.setVisibility(View.GONE);
        root.addView(textPanel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(p8, p8, p8, (int)(4 * d));
        textPanel.addView(modeRow);

        final Button bSel = new Button(this);
        bSel.setText("Select (No KB)"); bSel.setTextSize(11); bSel.setAllCaps(false);
        bSel.setTextColor(Color.WHITE); bSel.setBackgroundColor(Color.parseColor("#34C759"));
        modeRow.addView(bSel, new LinearLayout.LayoutParams(0, (int)(34 * d), 1f));

        final Button bTyp = new Button(this);
        bTyp.setText("Type (KB)"); bTyp.setTextSize(11); bTyp.setAllCaps(false);
        bTyp.setTextColor(Color.WHITE); bTyp.setBackgroundColor(Color.parseColor("#3A3A3C"));
        LinearLayout.LayoutParams btl = new LinearLayout.LayoutParams(0, (int)(34 * d), 1f);
        btl.setMargins((int)(6 * d), 0, (int)(6 * d), 0);
        modeRow.addView(bTyp, btl);

        Button bDone = new Button(this);
        bDone.setText("Done"); bDone.setTextSize(12); bDone.setAllCaps(false);
        bDone.setTextColor(Color.WHITE); bDone.setBackgroundColor(Color.parseColor("#007AFF"));
        modeRow.addView(bDone, new LinearLayout.LayoutParams(-2, (int)(34 * d)));

        edBox = new EditText(this);
        edBox.setText(doc, TextView.BufferType.EDITABLE);
        edBox.setTextColor(Color.WHITE);
        edBox.setBackgroundColor(Color.parseColor("#2C2C2E"));
        edBox.setTextSize(15);
        edBox.setPadding(p8 * 2, p8, p8 * 2, p8);
        edBox.setMaxLines(4);
        edBox.setShowSoftInputOnFocus(false);
        LinearLayout.LayoutParams ebl = new LinearLayout.LayoutParams(-1, -2);
        ebl.setMargins(p8, (int)(4 * d), p8, (int)(4 * d));
        textPanel.addView(edBox, ebl);

        final Runnable sync = () -> {
            cText.setText(edBox.getText(), TextView.BufferType.SPANNABLE);
            cText.invalidate();
        };

        edBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { sync.run(); }
        });

        bSel.setOnClickListener(v -> {
            edBox.setShowSoftInputOnFocus(false);
            bSel.setBackgroundColor(Color.parseColor("#34C759"));
            bTyp.setBackgroundColor(Color.parseColor("#3A3A3C"));
            hideKeyboard();
        });

        bTyp.setOnClickListener(v -> {
            edBox.setShowSoftInputOnFocus(true);
            edBox.requestFocus();
            bTyp.setBackgroundColor(Color.parseColor("#34C759"));
            bSel.setBackgroundColor(Color.parseColor("#3A3A3C"));
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(edBox, InputMethodManager.SHOW_IMPLICIT);
        });

        final LinearLayout subPanel = new LinearLayout(this);
        subPanel.setOrientation(LinearLayout.HORIZONTAL);
        subPanel.setBackgroundColor(Color.parseColor("#18181A"));
        subPanel.setGravity(Gravity.CENTER_VERTICAL);
        subPanel.setPadding(p8, p8, p8, p8);
        subPanel.setVisibility(View.GONE);
        textPanel.addView(subPanel, new LinearLayout.LayoutParams(-1, (int)(50 * d)));

        HorizontalScrollView hsvTools = new HorizontalScrollView(this);
        hsvTools.setBackgroundColor(Color.parseColor("#252525"));
        textPanel.addView(hsvTools, new LinearLayout.LayoutParams(-1, (int)(48 * d)));
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setPadding(p8, (int)(6 * d), p8, (int)(6 * d));
        hsvTools.addView(toolRow);

        // --- TIMELINE & PLAYBACK ---
        final LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.VERTICAL);
        timeline.setBackgroundColor(Color.parseColor("#181818"));
        timeline.setPadding(p8, p8, p8, p8);
        root.addView(timeline, new LinearLayout.LayoutParams(-1, (int)(92 * d)));

        LinearLayout playRow = new LinearLayout(this);
        playRow.setOrientation(LinearLayout.HORIZONTAL);
        playRow.setGravity(Gravity.CENTER_VERTICAL);
        timeline.addView(playRow);

        btnPlay = new Button(this);
        btnPlay.setText("Play");
        btnPlay.setTextSize(12);
        btnPlay.setTextColor(Color.WHITE);
        btnPlay.setBackgroundColor(Color.parseColor("#333333"));
        btnPlay.setOnClickListener(v -> togglePlay());
        playRow.addView(btnPlay, new LinearLayout.LayoutParams(-2, (int)(34 * d)));

        txtTime = new TextView(this);
        txtTime.setText("  00:00 / 00:00");
        txtTime.setTextColor(Color.LTGRAY);
        txtTime.setTextSize(12);
        playRow.addView(txtTime);

        timelineSeek = new SeekBar(this);
        timelineSeek.setMax(1000);
        timelineSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && selectedVideoUri != null) {
                    videoView.seekTo(progress);
                    updateTimeText(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        timeline.addView(timelineSeek, new LinearLayout.LayoutParams(-1, -2));

        // --- MAIN BOTTOM TOOLBAR ---
        final LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#1E1E1E"));
        bottomBar.setPadding(p8, p8, p8, p8);
        root.addView(bottomBar, new LinearLayout.LayoutParams(-1, (int)(56 * d)));

        bDone.setOnClickListener(v -> {
            textPanel.setVisibility(View.GONE);
            subPanel.setVisibility(View.GONE);
            timeline.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
            hideKeyboard();
        });

        final int[] stIdx = {0}, shIdx = {0}, alIdx = {0}, spIdx = {0};
        final int[] pal = {
            Color.parseColor("#FFD60A"), Color.parseColor("#0A84FF"),
            Color.parseColor("#FF453A"), Color.parseColor("#30D158"),
            Color.parseColor("#BF5AF2"), Color.parseColor("#FF9F0A"),
            Color.WHITE, Color.BLACK
        };

        String[] tBtns = {"Highlight", "Color", "Size", "Font", "Bold", "Italic", "Underline", "Stroke", "Shadow", "Align", "Spacing"};
        for (final String lbl : tBtns) {
            Button tb = new Button(this);
            tb.setText(lbl); tb.setTextColor(Color.WHITE);
            tb.setBackgroundColor(Color.parseColor("#3A3A3C"));
            tb.setTextSize(12); tb.setAllCaps(false);
            LinearLayout.LayoutParams tbp = new LinearLayout.LayoutParams(-2, -1);
            tbp.setMargins((int)(4 * d), 0, (int)(4 * d), 0);
            tb.setOnClickListener(v -> {
                if (lbl.equals("Highlight") || lbl.equals("Color")) {
                    subPanel.removeAllViews(); subPanel.setVisibility(View.VISIBLE);
                    HorizontalScrollView hsv = new HorizontalScrollView(this);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    hsv.addView(row); subPanel.addView(hsv);
                    if (lbl.equals("Highlight")) {
                        Button cb = new Button(this);
                        cb.setText("Clear"); cb.setTextSize(11); cb.setTextColor(Color.WHITE);
                        cb.setBackgroundColor(Color.parseColor("#444444"));
                        cb.setOnClickListener(cv -> {
                            int s = edBox.getSelectionStart(), e = edBox.getSelectionEnd();
                            if (s > e) { int t = s; s = e; e = t; }
                            if (s < 0 || s == e) return;
                            Spannable sp = edBox.getText();
                            for (HlSpan o : sp.getSpans(s, e, HlSpan.class)) {
                                int os = sp.getSpanStart(o), oe = sp.getSpanEnd(o);
                                sp.removeSpan(o);
                                if (os < s) sp.setSpan(new HlSpan(o.c), os, s, 33);
                                if (oe > e) sp.setSpan(new HlSpan(o.c), e, oe, 33);
                            }
                            sync.run();
                        });
                        row.addView(cb, new LinearLayout.LayoutParams(-2, (int)(32 * d)));
                    }
                    for (final int col : pal) {
                        View sw = new View(this);
                        GradientDrawable gd = new GradientDrawable();
                        gd.setShape(GradientDrawable.OVAL);
                        gd.setColor(col); gd.setStroke((int)(2 * d), Color.GRAY);
                        sw.setBackground(gd);
                        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams((int)(32 * d), (int)(32 * d));
                        slp.setMargins((int)(6 * d), 0, (int)(6 * d), 0);
                        sw.setOnClickListener(sv -> {
                            int s = edBox.getSelectionStart(), e = edBox.getSelectionEnd();
                            if (s > e) { int t = s; s = e; e = t; }
                            if (s < 0 || s == e) {
                                if (lbl.equals("Color")) { cText.setTextColor(col); return; }
                                Toast.makeText(this, "Long-press words in box to select", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            Spannable sp = edBox.getText();
                            if (lbl.equals("Highlight")) {
                                for (HlSpan o : sp.getSpans(s, e, HlSpan.class)) {
                                    int os = sp.getSpanStart(o), oe = sp.getSpanEnd(o);
                                    sp.removeSpan(o);
                                    if (os < s) sp.setSpan(new HlSpan(o.c), os, s, 33);
                                    if (oe > e) sp.setSpan(new HlSpan(o.c), e, oe, 33);
                                }
                                sp.setSpan(new HlSpan(col), s, e, 33);
                            } else {
                                sp.setSpan(new ForegroundColorSpan(col), s, e, 33);
                            }
                            sync.run();
                        });
                        row.addView(sw, slp);
                    }
                    return;
                }
                if (lbl.equals("Size")) {
                    subPanel.removeAllViews(); subPanel.setVisibility(View.VISIBLE);
                    final TextView sl = new TextView(this);
                    sl.setText("Size: 22"); sl.setTextColor(Color.WHITE); sl.setPadding(0, 0, p8, 0);
                    subPanel.addView(sl);
                    SeekBar sb = new SeekBar(this);
                    sb.setMax(48); sb.setProgress(10);
                    sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override public void onProgressChanged(SeekBar bar, int p, boolean u) {
                            int sz = 12 + p; sl.setText("Size: " + sz);
                            int s = edBox.getSelectionStart(), e = edBox.getSelectionEnd();
                            if (s > e) { int t = s; s = e; e = t; }
                            if (s >= 0 && e > s) { edBox.getText().setSpan(new AbsoluteSizeSpan(sz, true), s, e, 33); sync.run(); }
                            else { cText.setTextSize(sz); }
                        }
                        @Override public void onStartTrackingTouch(SeekBar bar) {}
                        @Override public void onStopTrackingTouch(SeekBar bar) {}
                    });
                    subPanel.addView(sb, new LinearLayout.LayoutParams(0, -2, 1f));
                    return;
                }
                if (lbl.equals("Font")) {
                    subPanel.removeAllViews(); subPanel.setVisibility(View.VISIBLE);
                    HorizontalScrollView hsv = new HorizontalScrollView(this);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    hsv.addView(row); subPanel.addView(hsv);
                    String[] fn = {"Default", "Serif", "Mono", "Bold", "Classic", "Condensed"};
                    Typeface[] tfs = {
                        Typeface.SANS_SERIF, Typeface.SERIF, Typeface.MONOSPACE, Typeface.DEFAULT_BOLD,
                        Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC),
                        Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    };
                    for (int i = 0; i < fn.length; i++) {
                        final Typeface tf = tfs[i];
                        Button fb = new Button(this);
                        fb.setText(fn[i]); fb.setTypeface(tf); fb.setTextColor(Color.WHITE);
                        fb.setBackgroundColor(Color.parseColor("#333333")); fb.setTextSize(12); fb.setAllCaps(false);
                        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-2, (int)(32 * d));
                        flp.setMargins((int)(4 * d), 0, (int)(4 * d), 0);
                        fb.setOnClickListener(fv -> {
                            int s = edBox.getSelectionStart(), e = edBox.getSelectionEnd();
                            if (s > e) { int t = s; s = e; e = t; }
                            if (s >= 0 && e > s) { edBox.getText().setSpan(new TfSpan(tf), s, e, 33); sync.run(); }
                            else { cText.setTypeface(tf); }
                        });
                        row.addView(fb, flp);
                    }
                    return;
                }
                subPanel.setVisibility(View.GONE);
                if (lbl.equals("Stroke")) {
                    stIdx[0] = (stIdx[0] + 1) % 4;
                    cText.sw = stIdx[0] == 0 ? 0 : (stIdx[0] == 1 ? 4 * d : (stIdx[0] == 2 ? 8 * d : 6 * d));
                    cText.sc = stIdx[0] == 3 ? Color.WHITE : Color.BLACK;
                    cText.invalidate(); return;
                }
                if (lbl.equals("Shadow")) {
                    shIdx[0] = (shIdx[0] + 1) % 3;
                    if (shIdx[0] == 0) cText.setShadowLayer(0, 0, 0, 0);
                    else if (shIdx[0] == 1) cText.setShadowLayer(8, 2, 4, Color.BLACK);
                    else cText.setShadowLayer(2, 4, 4, Color.BLACK);
                    return;
                }
                if (lbl.equals("Align")) {
                    alIdx[0] = (alIdx[0] + 1) % 3;
                    cText.setGravity(alIdx[0] == 0 ? Gravity.CENTER : (alIdx[0] == 1 ? Gravity.START : Gravity.END));
                    return;
                }
                if (lbl.equals("Spacing")) {
                    spIdx[0] = (spIdx[0] + 1) % 3;
                    cText.setLetterSpacing(spIdx[0] == 0 ? 0f : (spIdx[0] == 1 ? 0.08f : 0.18f));
                    cText.setLineSpacing(0f, spIdx[0] == 0 ? 1f : (spIdx[0] == 1 ? 1.25f : 1.5f));
                    return;
                }
                int s = edBox.getSelectionStart(), e = edBox.getSelectionEnd();
                if (s > e) { int t = s; s = e; e = t; }
                if (s < 0 || s == e) { Toast.makeText(this, "Select words in box first", Toast.LENGTH_SHORT).show(); return; }
                Spannable sp = edBox.getText();
                if (lbl.equals("Bold")) sp.setSpan(new StyleSpan(Typeface.BOLD), s, e, 33);
                else if (lbl.equals("Italic")) sp.setSpan(new StyleSpan(Typeface.ITALIC), s, e, 33);
                else if (lbl.equals("Underline")) sp.setSpan(new UnderlineSpan(), s, e, 33);
                sync.run();
            });
            toolRow.addView(tb, tbp);
        }

        String[] mTools = {"Text", "Video", "Audio", "Effects"};
        for (final String nm : mTools) {
            Button btn = new Button(this);
            btn.setText(nm); btn.setTextColor(Color.WHITE);
            btn.setBackgroundColor(Color.parseColor("#2A2A2A")); btn.setTextSize(12);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -1, 1f);
            bp.setMargins((int)(4 * d), 0, (int)(4 * d), 0);
            btn.setOnClickListener(v -> {
                if (nm.equals("Text")) {
                    timeline.setVisibility(View.GONE);
                    bottomBar.setVisibility(View.GONE);
                    textPanel.setVisibility(View.VISIBLE);
                } else if (nm.equals("Video")) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("video/*");
                    startActivityForResult(intent, REQ_PICK_VIDEO);
                } else {
                    Toast.makeText(this, nm + " tool coming in next update", Toast.LENGTH_SHORT).show();
                }
            });
            bottomBar.addView(btn, bp);
        }

        setContentView(root);
    }

    private void togglePlay() {
        if (selectedVideoUri == null) {
            Toast.makeText(this, "Tap Video button to pick a video first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isPlaying) {
            videoView.pause();
            isPlaying = false;
            btnPlay.setText("Play");
        } else {
            videoView.start();
            isPlaying = true;
            btnPlay.setText("Pause");
            startProgressLoop();
        }
    }

    private void startProgressLoop() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (isPlaying && videoView.isPlaying()) {
                    int pos = videoView.getCurrentPosition();
                    timelineSeek.setProgress(pos);
                    updateTimeText(pos);
                    handler.postDelayed(this, 100);
                }
            }
        }, 100);
    }

    private void updateTimeText(int posMs) {
        txtTime.setText("  " + fmt(posMs) + " / " + fmt(videoDurationMs));
    }

    private String fmt(int ms) {
        int sec = ms / 1000;
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && edBox != null) imm.hideSoftInputFromWindow(edBox.getWindowToken(), 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_VIDEO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedVideoUri = data.getData();
            videoView.setVideoURI(selectedVideoUri);
            videoView.setOnPreparedListener(mp -> {
                videoDurationMs = mp.getDuration();
                timelineSeek.setMax(videoDurationMs);
                updateTimeText(0);
                mp.setLooping(true);
                videoView.start();
                isPlaying = true;
                btnPlay.setText("Pause");
                startProgressLoop();
            });
        }
    }
}
